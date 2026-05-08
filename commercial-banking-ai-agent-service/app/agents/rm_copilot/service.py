"""
Service layer for the RM Copilot (Meeting Assistant) agent.
Manages meeting lifecycle: schedule, notes, completion with AI-generated briefs and summaries.
"""

import logging
from datetime import datetime
from typing import Optional

from sqlalchemy.orm import Session

from app.agents.rm_copilot.graph import post_meeting_graph, pre_meeting_brief_graph
from app.models.db_models import RmMeeting, RmMeetingActionItem, RmMeetingNote
from app.models.enums import ActionItemStatus, MeetingStatus, NoteType, Priority
from app.models.schemas import (
    ActionItemDto,
    RmMeetingDto,
    RmMeetingNoteDto,
    ScheduleMeetingRequest,
)
from app.services.core_banking_client import core_banking_client

logger = logging.getLogger(__name__)


class RmCopilotService:
    """Manages RM meetings with AI-powered briefs, summaries, and action items."""

    def __init__(self, db: Session):
        self.db = db

    async def schedule_meeting(self, request: ScheduleMeetingRequest) -> RmMeetingDto:
        """
        Schedule a new meeting and auto-generate a pre-meeting brief via LangGraph.
        Fetches client data and optionally the latest health score for context.
        """
        logger.info(
            "Scheduling meeting: RM=%s client=%s date=%s",
            request.rm_user_id,
            request.client_identification,
            request.meeting_date,
        )

        # Look up the client in core-banking-service
        user = await core_banking_client.get_user(request.client_identification)
        client_user_id = user.id if user else 0

        # Try to fetch existing health data for the pre-meeting brief
        health_score = 0.0
        risk_level = "UNKNOWN"
        health_summary = "No health assessment available yet."
        try:
            from app.models.db_models import ClientHealthScore

            latest = (
                self.db.query(ClientHealthScore)
                .filter(ClientHealthScore.identification == request.client_identification)
                .order_by(ClientHealthScore.assessed_at.desc())
                .first()
            )
            if latest:
                health_score = float(latest.health_score) if latest.health_score else 0.0
                risk_level = latest.risk_level or "UNKNOWN"
                health_summary = latest.ai_summary or "Assessment available but no summary."
        except Exception as e:
            logger.warning("Could not fetch health data: %s", str(e))

        # Run the pre-meeting brief LangGraph
        initial_state = {
            "identification": request.client_identification,
            "meeting_date": str(request.meeting_date),
            "user_data": None,
            "account_details": "",
            "health_score": health_score,
            "risk_level": risk_level,
            "health_summary": health_summary,
            "brief": "",
            "error": None,
        }
        final_state = await pre_meeting_brief_graph.ainvoke(initial_state)

        # Persist the meeting
        meeting = RmMeeting(
            rm_user_id=request.rm_user_id,
            client_user_id=client_user_id,
            client_identification=request.client_identification,
            meeting_date=request.meeting_date,
            status=MeetingStatus.SCHEDULED.value,
            pre_meeting_brief=final_state.get("brief", ""),
        )
        self.db.add(meeting)
        self.db.commit()
        self.db.refresh(meeting)

        logger.info("Meeting %d scheduled with pre-meeting brief generated", meeting.id)
        return self._to_dto(meeting)

    def start_meeting(self, meeting_id: int) -> RmMeetingDto:
        """Change meeting status to IN_PROGRESS."""
        meeting = self._get_meeting(meeting_id)
        meeting.status = MeetingStatus.IN_PROGRESS.value
        self.db.commit()
        self.db.refresh(meeting)
        return self._to_dto(meeting)

    def add_note(self, meeting_id: int, content: str, note_type: str = "MANUAL") -> RmMeetingNoteDto:
        """Add a note to a meeting."""
        meeting = self._get_meeting(meeting_id)
        note = RmMeetingNote(
            meeting_id=meeting.id,
            content=content,
            note_type=note_type,
        )
        self.db.add(note)
        self.db.commit()
        self.db.refresh(note)
        return RmMeetingNoteDto.model_validate(note)

    async def complete_meeting(self, meeting_id: int) -> RmMeetingDto:
        """
        Complete a meeting: generate post-meeting summary and extract action items via LangGraph.
        """
        meeting = self._get_meeting(meeting_id)

        # Collect all notes for the meeting
        notes = (
            self.db.query(RmMeetingNote)
            .filter(RmMeetingNote.meeting_id == meeting_id)
            .order_by(RmMeetingNote.created_at.asc())
            .all()
        )
        meeting_notes_text = "\n\n".join(n.content for n in notes) if notes else "No notes recorded."

        # Build client name from stored data or fallback
        client_name = f"Client {meeting.client_identification}"
        try:
            user = await core_banking_client.get_user(meeting.client_identification)
            if user:
                client_name = f"{user.first_name} {user.last_name}".strip()
        except Exception:
            pass

        # Run the post-meeting LangGraph
        initial_state = {
            "identification": meeting.client_identification,
            "client_name": client_name,
            "meeting_date": str(meeting.meeting_date),
            "meeting_notes": meeting_notes_text,
            "summary": "",
            "action_items": [],
            "error": None,
        }
        final_state = await post_meeting_graph.ainvoke(initial_state)

        # Update meeting with summary
        meeting.post_meeting_summary = final_state.get("summary", "")
        meeting.status = MeetingStatus.COMPLETED.value

        # Persist extracted action items
        for item_data in final_state.get("action_items", []):
            action_item = RmMeetingActionItem(
                meeting_id=meeting.id,
                description=item_data.get("description", ""),
                assignee=item_data.get("assignee", "RM"),
                due_date=item_data.get("due_date"),
                status=ActionItemStatus.OPEN.value,
                priority=item_data.get("priority", Priority.MEDIUM.value),
            )
            self.db.add(action_item)

        self.db.commit()
        self.db.refresh(meeting)

        logger.info(
            "Meeting %d completed: %d action items extracted",
            meeting.id,
            len(final_state.get("action_items", [])),
        )
        return self._to_dto(meeting)

    def get_meeting(self, meeting_id: int) -> RmMeetingDto:
        """Get meeting details by ID."""
        return self._to_dto(self._get_meeting(meeting_id))

    def get_meetings_for_rm(self, rm_user_id: str) -> list[RmMeetingDto]:
        """List all meetings for an RM, ordered by date descending."""
        meetings = (
            self.db.query(RmMeeting)
            .filter(RmMeeting.rm_user_id == rm_user_id)
            .order_by(RmMeeting.meeting_date.desc())
            .all()
        )
        return [self._to_dto(m) for m in meetings]

    def update_action_item_status(self, action_item_id: int, status: str) -> None:
        """Update the status of a meeting action item."""
        item = (
            self.db.query(RmMeetingActionItem)
            .filter(RmMeetingActionItem.id == action_item_id)
            .first()
        )
        if not item:
            raise ValueError(f"Action item {action_item_id} not found")
        item.status = status
        self.db.commit()

    def get_open_action_items(self, rm_user_id: str) -> list[ActionItemDto]:
        """Get all open action items for meetings belonging to an RM."""
        items = (
            self.db.query(RmMeetingActionItem)
            .join(RmMeeting, RmMeeting.id == RmMeetingActionItem.meeting_id)
            .filter(
                RmMeeting.rm_user_id == rm_user_id,
                RmMeetingActionItem.status != ActionItemStatus.COMPLETED.value,
            )
            .order_by(RmMeetingActionItem.created_at.desc())
            .all()
        )
        return [ActionItemDto.model_validate(i) for i in items]

    # --- Private helpers ---

    def _get_meeting(self, meeting_id: int) -> RmMeeting:
        """Fetch a meeting by ID or raise ValueError."""
        meeting = self.db.query(RmMeeting).filter(RmMeeting.id == meeting_id).first()
        if not meeting:
            raise ValueError(f"Meeting {meeting_id} not found")
        return meeting

    def _to_dto(self, meeting: RmMeeting) -> RmMeetingDto:
        """Convert a meeting ORM entity to a DTO, including nested notes and action items."""
        return RmMeetingDto(
            id=meeting.id,
            rm_user_id=meeting.rm_user_id,
            client_user_id=meeting.client_user_id,
            client_identification=meeting.client_identification,
            meeting_date=meeting.meeting_date,
            status=meeting.status,
            pre_meeting_brief=meeting.pre_meeting_brief,
            post_meeting_summary=meeting.post_meeting_summary,
            action_items=[ActionItemDto.model_validate(a) for a in (meeting.action_items or [])],
            notes=[RmMeetingNoteDto.model_validate(n) for n in (meeting.notes or [])],
            created_at=meeting.created_at,
        )

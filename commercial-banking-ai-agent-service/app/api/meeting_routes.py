"""
FastAPI routes for the RM Copilot Meeting Assistant agent (Agent 2).
Provides endpoints for meeting lifecycle: schedule, notes, completion, and action items.
"""

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.agents.rm_copilot.service import RmCopilotService
from app.db.database import get_db
from app.models.schemas import (
    ActionItemDto,
    AddNoteRequest,
    RmMeetingDto,
    RmMeetingNoteDto,
    ScheduleMeetingRequest,
    UpdateActionItemRequest,
)

# Router mounted at /api/v1/meetings in the main app
router = APIRouter(prefix="/api/v1/meetings", tags=["RM Copilot - Meeting Assistant"])


@router.post(
    "/schedule",
    response_model=RmMeetingDto,
    summary="Schedule a new RM-client meeting",
)
async def schedule_meeting(request: ScheduleMeetingRequest, db: Session = Depends(get_db)):
    """
    Schedule a meeting and auto-generate a pre-meeting brief via LangGraph.
    The brief pulls client data and health scores for comprehensive context.
    """
    try:
        service = RmCopilotService(db)
        return await service.schedule_meeting(request)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.get(
    "/{meeting_id}",
    response_model=RmMeetingDto,
    summary="Get meeting details by ID",
)
async def get_meeting(meeting_id: int, db: Session = Depends(get_db)):
    """Retrieve full meeting details including brief, summary, notes, and action items."""
    try:
        service = RmCopilotService(db)
        return service.get_meeting(meeting_id)
    except ValueError as e:
        raise HTTPException(status_code=404, detail=str(e))


@router.put(
    "/{meeting_id}/start",
    response_model=RmMeetingDto,
    summary="Start a meeting (change status to IN_PROGRESS)",
)
async def start_meeting(meeting_id: int, db: Session = Depends(get_db)):
    """Transition a scheduled meeting to IN_PROGRESS status."""
    try:
        service = RmCopilotService(db)
        return service.start_meeting(meeting_id)
    except ValueError as e:
        raise HTTPException(status_code=404, detail=str(e))


@router.post(
    "/{meeting_id}/notes",
    response_model=RmMeetingNoteDto,
    summary="Add a note to a meeting",
)
async def add_note(meeting_id: int, request: AddNoteRequest, db: Session = Depends(get_db)):
    """Add a manual or AI-generated note to an active meeting."""
    try:
        service = RmCopilotService(db)
        return service.add_note(meeting_id, request.content, request.note_type)
    except ValueError as e:
        raise HTTPException(status_code=404, detail=str(e))


@router.put(
    "/{meeting_id}/complete",
    response_model=RmMeetingDto,
    summary="Complete a meeting with AI-generated summary and action items",
)
async def complete_meeting(meeting_id: int, db: Session = Depends(get_db)):
    """
    Complete a meeting: runs the post-meeting LangGraph to generate a summary
    and extract action items from the collected notes.
    """
    try:
        service = RmCopilotService(db)
        return await service.complete_meeting(meeting_id)
    except ValueError as e:
        raise HTTPException(status_code=404, detail=str(e))


@router.get(
    "/rm/{rm_user_id}",
    response_model=list[RmMeetingDto],
    summary="List all meetings for an RM",
)
async def get_meetings_for_rm(rm_user_id: str, db: Session = Depends(get_db)):
    """Retrieve all meetings for a specific Relationship Manager, ordered by date."""
    service = RmCopilotService(db)
    return service.get_meetings_for_rm(rm_user_id)


@router.get(
    "/rm/{rm_user_id}/action-items",
    response_model=list[ActionItemDto],
    summary="Get open action items for an RM",
)
async def get_open_action_items(rm_user_id: str, db: Session = Depends(get_db)):
    """List all open (non-completed) action items across all meetings for an RM."""
    service = RmCopilotService(db)
    return service.get_open_action_items(rm_user_id)


@router.put(
    "/action-items/{action_item_id}",
    summary="Update an action item's status",
)
async def update_action_item(
    action_item_id: int,
    request: UpdateActionItemRequest,
    db: Session = Depends(get_db),
):
    """Update the status of a meeting action item (OPEN → IN_PROGRESS → COMPLETED)."""
    try:
        service = RmCopilotService(db)
        service.update_action_item_status(action_item_id, request.status)
        return {"message": f"Action item {action_item_id} updated to {request.status}"}
    except ValueError as e:
        raise HTTPException(status_code=404, detail=str(e))

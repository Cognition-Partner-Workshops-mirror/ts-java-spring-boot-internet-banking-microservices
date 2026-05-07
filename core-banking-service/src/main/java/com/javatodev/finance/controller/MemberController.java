package com.javatodev.finance.controller;

import com.javatodev.finance.model.dto.request.MemberCreateRequest;
import com.javatodev.finance.service.MemberService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Tag(name = "Member Controller", description = "APIs for managing banking members")
@RestController
@RequestMapping(value = "/api/v1/member")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @Operation(summary = "Create Member", description = "Add a new banking member")
    @PostMapping
    public ResponseEntity createMember(@RequestBody MemberCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(memberService.createMember(request));
    }

    @Operation(summary = "Read Member by ID", description = "Retrieve a member by their ID")
    @GetMapping(value = "/{id}")
    public ResponseEntity readMember(@PathVariable("id") Long id) {
        return ResponseEntity.ok(memberService.readMember(id));
    }

    @Operation(summary = "Read Member by Identification", description = "Retrieve a member by their identification number")
    @GetMapping(value = "/identification/{identification}")
    public ResponseEntity readMemberByIdentification(@PathVariable("identification") String identification) {
        return ResponseEntity.ok(memberService.readMemberByIdentification(identification));
    }

    @Operation(summary = "Read Members", description = "Retrieve a paginated list of members")
    @GetMapping
    public ResponseEntity readMembers(Pageable pageable) {
        return ResponseEntity.ok(memberService.readMembers(pageable));
    }

    @Operation(summary = "Update Member", description = "Update an existing banking member")
    @PutMapping(value = "/{id}")
    public ResponseEntity updateMember(@PathVariable("id") Long id, @RequestBody MemberCreateRequest request) {
        return ResponseEntity.ok(memberService.updateMember(id, request));
    }

    @Operation(summary = "Delete Member", description = "Delete a banking member by ID")
    @DeleteMapping(value = "/{id}")
    public ResponseEntity deleteMember(@PathVariable("id") Long id) {
        memberService.deleteMember(id);
        return ResponseEntity.noContent().build();
    }

}

package com.javatodev.finance.service;

import com.javatodev.finance.exception.EntityNotFoundException;
import com.javatodev.finance.model.dto.Member;
import com.javatodev.finance.model.dto.request.MemberCreateRequest;
import com.javatodev.finance.model.entity.MemberEntity;
import com.javatodev.finance.model.mapper.MemberMapper;
import com.javatodev.finance.repository.MemberRepository;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MemberService {

    private MemberMapper memberMapper = new MemberMapper();

    private final MemberRepository memberRepository;

    public Member createMember(MemberCreateRequest request) {
        MemberEntity entity = new MemberEntity();
        entity.setFirstName(request.getFirstName());
        entity.setLastName(request.getLastName());
        entity.setEmail(request.getEmail());
        entity.setPhoneNumber(request.getPhoneNumber());
        entity.setIdentificationNumber(request.getIdentificationNumber());
        entity.setMembershipType(request.getMembershipType());
        entity.setAddress(request.getAddress());
        entity.setDateOfBirth(request.getDateOfBirth());
        MemberEntity saved = memberRepository.save(entity);
        return memberMapper.convertToDto(saved);
    }

    public Member readMember(Long id) {
        MemberEntity entity = memberRepository.findById(id)
                .orElseThrow(EntityNotFoundException::new);
        return memberMapper.convertToDto(entity);
    }

    public Member readMemberByIdentification(String identification) {
        MemberEntity entity = memberRepository.findByIdentificationNumber(identification)
                .orElseThrow(EntityNotFoundException::new);
        return memberMapper.convertToDto(entity);
    }

    public List<Member> readMembers(Pageable pageable) {
        return memberMapper.convertToDtoList(memberRepository.findAll(pageable).getContent());
    }

    public Member updateMember(Long id, MemberCreateRequest request) {
        MemberEntity entity = memberRepository.findById(id)
                .orElseThrow(EntityNotFoundException::new);
        entity.setFirstName(request.getFirstName());
        entity.setLastName(request.getLastName());
        entity.setEmail(request.getEmail());
        entity.setPhoneNumber(request.getPhoneNumber());
        entity.setIdentificationNumber(request.getIdentificationNumber());
        entity.setMembershipType(request.getMembershipType());
        entity.setAddress(request.getAddress());
        entity.setDateOfBirth(request.getDateOfBirth());
        MemberEntity saved = memberRepository.save(entity);
        return memberMapper.convertToDto(saved);
    }

    public void deleteMember(Long id) {
        MemberEntity entity = memberRepository.findById(id)
                .orElseThrow(EntityNotFoundException::new);
        memberRepository.delete(entity);
    }
}

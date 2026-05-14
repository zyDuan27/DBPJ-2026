package com.campus.activity.service;

import com.campus.activity.common.Access;
import com.campus.activity.common.AuthContext;
import com.campus.activity.common.BusinessException;
import com.campus.activity.common.CurrentUser;
import com.campus.activity.common.PageResult;
import com.campus.activity.common.RegistrationStatus;
import com.campus.activity.common.Role;
import com.campus.activity.model.entity.Registration;
import com.campus.activity.model.mapper.ActivityMapper;
import com.campus.activity.model.mapper.CreditRecordMapper;
import com.campus.activity.model.mapper.RegistrationMapper;
import com.campus.activity.model.row.ActivityLockRow;
import com.campus.activity.model.row.RegistrationActionRow;
import com.campus.activity.model.row.RegistrationLockRow;
import com.campus.activity.model.vo.RegistrationActionVO;
import com.campus.activity.model.vo.RegistrationListItemVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class RegistrationService {
    private final RegistrationMapper registrationMapper;
    private final ActivityMapper activityMapper;
    private final CreditRecordMapper creditRecordMapper;

    public RegistrationService(RegistrationMapper registrationMapper,
                               ActivityMapper activityMapper,
                               CreditRecordMapper creditRecordMapper) {
        this.registrationMapper = registrationMapper;
        this.activityMapper = activityMapper;
        this.creditRecordMapper = creditRecordMapper;
    }

    @Transactional
    public RegistrationActionVO enroll(int activityId) {
        CurrentUser student = Access.require(Role.STUDENT);
        ActivityLockRow activity = lockActivity(activityId);
        validateActivityOpenForEnrollment(activity);

        RegistrationActionRow existing = findReusableRegistration(student.id(), activityId);
        if (existing != null) {
            return RegistrationActionVO.from(existing, null);
        }

        if (activity.getCurrentEnrollment() < activity.getCapacityLimit()) {
            return enrollDirectly(student.id(), activityId);
        }
        return waitlist(student.id(), activityId);
    }

    @Transactional
    public RegistrationActionVO cancel(int registrationId) {
        CurrentUser user = AuthContext.get();
        RegistrationLockRow registration = lockRegistration(registrationId);
        validateCancellationAllowed(user, registration.getStudentId(), registration.getStatus());

        if ("CANCELLED".equals(registration.getStatus())) {
            return RegistrationActionVO.status(registrationId, "CANCELLED");
        }

        registrationMapper.cancelRegistration(registrationId);
        Integer promotedId = "ENROLLED".equals(registration.getStatus())
                ? promoteNextWaitlisted(registration.getActivityId())
                : null;
        return RegistrationActionVO.cancelled(registrationId, promotedId);
    }

    public PageResult<RegistrationListItemVO> my(int page, int size, String status) {
        CurrentUser student = Access.require(Role.STUDENT);
        long total = registrationMapper.countMine(student.id(), status);
        List<RegistrationListItemVO> rows = registrationMapper.listMine(student.id(), status, (page - 1) * size, size)
                .stream()
                .map(RegistrationListItemVO::from)
                .toList();
        return new PageResult<>(rows, total, page, size);
    }

    public PageResult<RegistrationListItemVO> activityRegistrations(int activityId, int page, int size, String status) {
        CurrentUser user = Access.require(Role.ORGANIZER, Role.ADMIN);
        validateActivityAccess(activityId, user);
        long total = registrationMapper.countActivityRegistrations(activityId, status);
        List<RegistrationListItemVO> rows = registrationMapper.listActivityRegistrations(
                        activityId, status, (page - 1) * size, size)
                .stream()
                .map(RegistrationListItemVO::from)
                .toList();
        return new PageResult<>(rows, total, page, size);
    }

    @Transactional
    public RegistrationActionVO markAbsences(int activityId) {
        CurrentUser user = Access.require(Role.ORGANIZER, Role.ADMIN);
        validateActivityAccess(activityId, user);
        ActivityLockRow activity = lockActivity(activityId);
        LocalDateTime endTime = activity.getEndTime();
        if (endTime != null && LocalDateTime.now().isBefore(endTime)) {
            throw new BusinessException(40903, "活动结束后才能标记缺勤");
        }
        int absentCount = registrationMapper.markAbsences(activityId);
        if (absentCount > 0) {
            recordAbsences(activityId, absentCount, user.id());
        }
        return RegistrationActionVO.absent(activityId, absentCount);
    }

    private void validateActivityOpenForEnrollment(ActivityLockRow activity) {
        if (!"PUBLISHED".equals(activity.getStatus())) {
            throw new BusinessException(40904, "活动当前不可报名");
        }
        if (LocalDateTime.now().isAfter(activity.getEnrollDeadline())) {
            throw new BusinessException(40904, "报名已截止");
        }
    }

    private RegistrationActionRow findReusableRegistration(int studentId, int activityId) {
        RegistrationActionRow existing = registrationMapper.findReusableRegistration(studentId, activityId);
        if (existing == null) {
            return null;
        }
        String status = existing.getRegistrationStatus();
        if ("ENROLLED".equals(status) || "WAITLISTED".equals(status) || "CHECKED_IN".equals(status)) {
            return existing;
        }
        return null;
    }

    private RegistrationActionVO enrollDirectly(int studentId, int activityId) {
        int registrationId = upsertRegistration(studentId, activityId, "ENROLLED", null);
        activityMapper.incrementEnrollment(activityId);
        return RegistrationActionVO.enrollment(
                registrationId,
                activityId,
                RegistrationStatus.ENROLLED.name(),
                null,
                "报名成功"
        );
    }

    private RegistrationActionVO waitlist(int studentId, int activityId) {
        Integer queueNo = registrationMapper.nextWaitlistQueueNo(activityId);
        int registrationId = upsertRegistration(studentId, activityId, "WAITLISTED", queueNo);
        return RegistrationActionVO.enrollment(
                registrationId,
                activityId,
                RegistrationStatus.WAITLISTED.name(),
                queueNo,
                "名额已满，已进入候补队列"
        );
    }

    private void validateCancellationAllowed(CurrentUser user, int studentId, String status) {
        if (user.role() != Role.ADMIN && studentId != user.id()) {
            throw new BusinessException(40301, "只能取消自己的报名");
        }
        if ("CHECKED_IN".equals(status)) {
            throw new BusinessException(40903, "已签到记录不能取消");
        }
    }

    private Integer promoteNextWaitlisted(int activityId) {
        lockActivity(activityId);
        activityMapper.decrementEnrollment(activityId);
        Integer promotedId = registrationMapper.findNextWaitlisted(activityId);
        if (promotedId == null) {
            return null;
        }
        registrationMapper.promoteRegistration(promotedId);
        activityMapper.incrementEnrollment(activityId);
        return promotedId;
    }

    private void recordAbsences(int activityId, int absentCount, int operatorId) {
        activityMapper.decreaseEnrollmentBy(activityId, absentCount);
        creditRecordMapper.insertAbsenceCredits(activityId, operatorId);
    }

    private int upsertRegistration(int studentId, int activityId, String status, Integer queueNo) {
        Integer existingId = registrationMapper.findRegistrationId(studentId, activityId);
        Registration registration = new Registration();
        registration.setStudentId(studentId);
        registration.setActivityId(activityId);
        registration.setStatus(status);
        registration.setQueueNo(queueNo);
        if (existingId == null) {
            registrationMapper.insertRegistration(registration);
            return registration.getRegistrationId();
        }
        registration.setRegistrationId(existingId);
        registrationMapper.updateEnrollment(registration);
        return existingId;
    }

    private ActivityLockRow lockActivity(int activityId) {
        ActivityLockRow activity = activityMapper.lockActivity(activityId);
        if (activity == null) {
            throw new BusinessException(40401, "活动不存在");
        }
        return activity;
    }

    private RegistrationLockRow lockRegistration(int registrationId) {
        RegistrationLockRow registration = registrationMapper.lockRegistration(registrationId);
        if (registration == null) {
            throw new BusinessException(40401, "报名记录不存在");
        }
        return registration;
    }

    private void validateActivityAccess(int activityId, CurrentUser user) {
        if (user.role() == Role.ADMIN) {
            return;
        }
        Integer ownerId = activityMapper.findOrganizerId(activityId);
        if (ownerId == null || ownerId != user.id()) {
            throw new BusinessException(40301, "只能查看自己活动的名单");
        }
    }
}

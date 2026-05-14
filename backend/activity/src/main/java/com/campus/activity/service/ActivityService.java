package com.campus.activity.service;

import com.campus.activity.common.Access;
import com.campus.activity.common.ActivityStatus;
import com.campus.activity.common.AuthContext;
import com.campus.activity.common.BusinessException;
import com.campus.activity.common.CurrentUser;
import com.campus.activity.common.PageResult;
import com.campus.activity.common.Role;
import com.campus.activity.model.dto.ActivityRequest;
import com.campus.activity.model.dto.ReviewRequest;
import com.campus.activity.model.entity.Activity;
import com.campus.activity.model.mapper.ActivityMapper;
import com.campus.activity.model.mapper.RegistrationMapper;
import com.campus.activity.model.row.ActivityDetailRow;
import com.campus.activity.model.row.StudentRegistrationRow;
import com.campus.activity.model.vo.ActivityDetailVO;
import com.campus.activity.model.vo.ActivityListItemVO;
import com.campus.activity.model.vo.ActivityMutationVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ActivityService {
    private static final List<String> EDITABLE_STATUS = List.of("DRAFT", "REJECTED");
    private static final String REVIEW_APPROVED = "APPROVED";
    private static final String REVIEW_REJECTED = "REJECTED";

    private final ActivityMapper activityMapper;
    private final RegistrationMapper registrationMapper;
    private final NotificationService notificationService;

    public ActivityService(ActivityMapper activityMapper,
                           RegistrationMapper registrationMapper,
                           NotificationService notificationService) {
        this.activityMapper = activityMapper;
        this.registrationMapper = registrationMapper;
        this.notificationService = notificationService;
    }

    public PageResult<ActivityListItemVO> list(int page, int size, String keyword, Integer campusId,
                                               Integer categoryId, String status, boolean mine) {
        CurrentUser user = AuthContext.get();
        boolean studentVisible = user.role() == Role.STUDENT;
        Integer organizerId = mine ? user.id() : null;
        long total = activityMapper.countActivities(studentVisible, organizerId, keyword, campusId, categoryId, status);
        List<ActivityListItemVO> rows = activityMapper.listActivities(
                        studentVisible, organizerId, keyword, campusId, categoryId, status, (page - 1) * size, size)
                .stream()
                .map(ActivityListItemVO::from)
                .toList();
        return new PageResult<>(rows, total, page, size);
    }

    public ActivityDetailVO detail(int activityId) {
        ActivityDetailRow detail = activityMapper.findDetail(activityId);
        if (detail == null) {
            throw new BusinessException(40401, "活动不存在");
        }
        CurrentUser user = AuthContext.get();
        StudentRegistrationRow registration = user.role() == Role.STUDENT
                ? activityMapper.findStudentRegistration(user.id(), activityId)
                : null;
        return ActivityDetailVO.from(detail, registration);
    }

    @Transactional
    public ActivityMutationVO create(ActivityRequest request) {
        CurrentUser user = Access.require(Role.ORGANIZER, Role.ADMIN);
        validateTime(request);
        Activity activity = toActivity(request);
        activity.setStatus(ActivityStatus.DRAFT.name());
        activity.setOrganizerId(user.id());
        activityMapper.insertActivity(activity);
        return ActivityMutationVO.created(activity.getActivityId(), ActivityStatus.DRAFT.name());
    }

    @Transactional
    public ActivityMutationVO update(int activityId, ActivityRequest request) {
        CurrentUser user = Access.require(Role.ORGANIZER, Role.ADMIN);
        validateOwnerOrAdmin(activityId, user);
        String status = findActivityStatus(activityId);
        if (user.role() != Role.ADMIN && !EDITABLE_STATUS.contains(status)) {
            throw new BusinessException(40903, "当前状态不允许修改活动");
        }
        validateTime(request);
        Activity activity = toActivity(request);
        activity.setActivityId(activityId);
        activityMapper.updateActivity(activity);
        return ActivityMutationVO.updatedResult();
    }

    @Transactional
    public ActivityMutationVO submit(int activityId) {
        CurrentUser user = Access.require(Role.ORGANIZER, Role.ADMIN);
        validateOwnerOrAdmin(activityId, user);
        String status = findActivityStatus(activityId);
        if (!EDITABLE_STATUS.contains(status)) {
            throw new BusinessException(40903, "只有草稿或驳回活动可以提交审核");
        }
        validateVenueConflict(activityId);
        activityMapper.submitForReview(activityId);
        return ActivityMutationVO.status(ActivityStatus.PENDING_REVIEW.name());
    }

    @Transactional
    public ActivityMutationVO review(int activityId, ReviewRequest request) {
        CurrentUser admin = Access.require(Role.ADMIN);
        String status = findActivityStatus(activityId);
        if (!"PENDING_REVIEW".equals(status)) {
            throw new BusinessException(40903, "只有待审核活动可以审核");
        }
        if (REVIEW_APPROVED.equals(request.result())) {
            return approve(activityId, admin.id());
        }
        if (REVIEW_REJECTED.equals(request.result())) {
            return reject(activityId, admin.id(), request.reason());
        }
        throw new BusinessException(40001, "审核结果不合法");
    }

    @Transactional
    public ActivityMutationVO cancel(int activityId) {
        CurrentUser user = Access.require(Role.ORGANIZER, Role.ADMIN);
        validateOwnerOrAdmin(activityId, user);
        var activity = activityMapper.findNotificationTarget(activityId);
        List<Integer> studentIds = registrationMapper.findActiveStudentIds(activityId);
        activityMapper.cancelActivity(activityId);
        if (activity != null) {
            notificationService.createMany(
                    studentIds,
                    "ACTIVITY_CANCELLED",
                    "活动已取消",
                    "你报名或候补的《" + activity.getTitle() + "》已取消。",
                    "ACTIVITY",
                    activityId
            );
            if (user.role() == Role.ADMIN && activity.getOrganizerId() != null && activity.getOrganizerId() != user.id()) {
                notificationService.create(
                        activity.getOrganizerId(),
                        "ACTIVITY_CANCELLED",
                        "活动已取消",
                        "管理员已取消《" + activity.getTitle() + "》。",
                        "ACTIVITY",
                        activityId
                );
            }
        }
        return ActivityMutationVO.status(ActivityStatus.CANCELLED.name());
    }

    private ActivityMutationVO approve(int activityId, int adminId) {
        validateVenueConflict(activityId);
        activityMapper.approveActivity(activityId, adminId);
        var activity = activityMapper.findNotificationTarget(activityId);
        if (activity != null) {
            notificationService.create(
                    activity.getOrganizerId(),
                    "ACTIVITY_APPROVED",
                    "活动审核通过",
                    "《" + activity.getTitle() + "》已发布。",
                    "ACTIVITY",
                    activityId
            );
        }
        return ActivityMutationVO.status(ActivityStatus.PUBLISHED.name());
    }

    private ActivityMutationVO reject(int activityId, int adminId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(40001, "驳回原因不能为空");
        }
        activityMapper.rejectActivity(activityId, adminId, reason);
        var activity = activityMapper.findNotificationTarget(activityId);
        if (activity != null) {
            notificationService.create(
                    activity.getOrganizerId(),
                    "ACTIVITY_REJECTED",
                    "活动审核驳回",
                    "《" + activity.getTitle() + "》审核未通过，原因：" + reason,
                    "ACTIVITY",
                    activityId
            );
        }
        return ActivityMutationVO.status(ActivityStatus.REJECTED.name());
    }

    private String findActivityStatus(int activityId) {
        String status = activityMapper.findStatus(activityId);
        if (status == null) {
            throw new BusinessException(40401, "活动不存在");
        }
        return status;
    }

    private void validateOwnerOrAdmin(int activityId, CurrentUser user) {
        Integer ownerId = activityMapper.findOrganizerId(activityId);
        if (ownerId == null) {
            throw new BusinessException(40401, "活动不存在");
        }
        if (user.role() != Role.ADMIN && ownerId != user.id()) {
            throw new BusinessException(40301, "只能管理自己创建的活动");
        }
    }

    private void validateTime(ActivityRequest request) {
        if (!request.endTime().isAfter(request.startTime())) {
            throw new BusinessException(40001, "结束时间必须晚于开始时间");
        }
        if (request.enrollDeadline().isAfter(request.startTime())) {
            throw new BusinessException(40001, "报名截止时间不得晚于活动开始时间");
        }
    }

    private void validateVenueConflict(int activityId) {
        Integer count = activityMapper.countVenueConflicts(activityId);
        if (count != null && count > 0) {
            throw new BusinessException(40902, "该场地在此时段已被占用");
        }
    }

    private Activity toActivity(ActivityRequest request) {
        Activity activity = new Activity();
        activity.setTitle(request.title());
        activity.setVenueId(request.venueId());
        activity.setCategoryId(request.categoryId());
        activity.setStartTime(request.startTime());
        activity.setEndTime(request.endTime());
        activity.setEnrollDeadline(request.enrollDeadline());
        activity.setCapacityLimit(request.capacityLimit());
        activity.setPosterUrl(request.posterUrl());
        activity.setDescription(request.description());
        return activity;
    }
}

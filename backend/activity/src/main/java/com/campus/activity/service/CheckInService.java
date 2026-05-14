package com.campus.activity.service;

import com.campus.activity.common.Access;
import com.campus.activity.common.BusinessException;
import com.campus.activity.common.CurrentUser;
import com.campus.activity.common.Role;
import com.campus.activity.model.dto.CheckInRequest;
import com.campus.activity.model.mapper.CreditRecordMapper;
import com.campus.activity.model.mapper.RegistrationMapper;
import com.campus.activity.model.row.CheckInTargetRow;
import com.campus.activity.model.row.RegistrationNotifyRow;
import com.campus.activity.model.vo.CheckInCodeVO;
import com.campus.activity.model.vo.CheckInResultVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;

@Service
@Transactional(readOnly = true)
public class CheckInService {
    private static final int CHECK_IN_CODE_TTL_SECONDS = 2 * 3600;
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final RegistrationMapper registrationMapper;
    private final CreditRecordMapper creditRecordMapper;
    private final NotificationService notificationService;
    private final String secret;

    public CheckInService(RegistrationMapper registrationMapper,
                          CreditRecordMapper creditRecordMapper,
                          NotificationService notificationService,
                          @Value("${app.auth-secret}") String secret) {
        this.registrationMapper = registrationMapper;
        this.creditRecordMapper = creditRecordMapper;
        this.notificationService = notificationService;
        this.secret = secret;
    }

    public CheckInCodeVO code(int registrationId) {
        CurrentUser student = Access.require(Role.STUDENT);
        CheckInTargetRow row = registrationMapper.findCheckInCodeTarget(registrationId);
        if (row == null) {
            throw new BusinessException(40401, "报名记录不存在");
        }
        validateCanGenerateCode(row, student);

        long expiresAt = Instant.now().plusSeconds(CHECK_IN_CODE_TTL_SECONDS).getEpochSecond();
        String payload = registrationId + ":" + expiresAt;
        String token = base64(payload) + "." + sign(payload);
        return new CheckInCodeVO(
                registrationId,
                token,
                LocalDateTime.ofInstant(Instant.ofEpochSecond(expiresAt), ZoneId.systemDefault()).toString()
        );
    }

    @Transactional
    public CheckInResultVO checkIn(CheckInRequest request) {
        CurrentUser user = Access.require(Role.ORGANIZER, Role.ADMIN);
        int registrationId = parseCode(request.checkInCode());
        CheckInTargetRow row = registrationMapper.findCheckInTargetForUpdate(registrationId);
        if (row == null) {
            throw new BusinessException(40401, "报名记录不存在");
        }
        validateCanCheckIn(row, user);

        String status = row.getStatus();
        if ("CHECKED_IN".equals(status)) {
            return new CheckInResultVO(registrationId, "CHECKED_IN", null);
        }
        if (!"ENROLLED".equals(status)) {
            throw new BusinessException(40903, "只有正选报名可以签到");
        }
        registrationMapper.markCheckedIn(registrationId);
        creditRecordMapper.insertCheckInCredit(user.id(), registrationId);
        RegistrationNotifyRow target = registrationMapper.findNotificationTarget(registrationId);
        if (target != null) {
            notificationService.create(
                    target.getStudentId(),
                    "CHECK_IN_SUCCESS",
                    "签到成功",
                    "你已完成《" + target.getTitle() + "》签到。",
                    "REGISTRATION",
                    registrationId
            );
        }
        return new CheckInResultVO(registrationId, "CHECKED_IN", LocalDateTime.now().toString());
    }

    private void validateCanGenerateCode(CheckInTargetRow row, CurrentUser student) {
        if (!row.getStudentId().equals(student.id())) {
            throw new BusinessException(40301, "只能生成自己的签到码");
        }
        if (!"ENROLLED".equals(row.getStatus())) {
            throw new BusinessException(40903, "当前报名状态不可签到");
        }
    }

    private void validateCanCheckIn(CheckInTargetRow row, CurrentUser user) {
        int organizerId = row.getOrganizerId();
        if (user.role() != Role.ADMIN && organizerId != user.id()) {
            throw new BusinessException(40301, "只能核销自己活动的签到");
        }
    }

    private int parseCode(String token) {
        if (token == null || !token.contains(".")) {
            throw new BusinessException(40001, "签到码格式错误");
        }
        String[] parts = token.split("\\.", 2);
        String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        if (!sign(payload).equals(parts[1])) {
            throw new BusinessException(40001, "签到码无效");
        }
        String[] values = payload.split(":");
        long expiresAt = Long.parseLong(values[1]);
        if (Instant.now().getEpochSecond() > expiresAt) {
            throw new BusinessException(40903, "签到码已过期");
        }
        return Integer.parseInt(values[0]);
    }

    private String base64(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("签到码签名失败", ex);
        }
    }
}

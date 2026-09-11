package com.couple.service.user.service;

import com.couple.common.security.CurrentUser;
import com.couple.service.user.dto.PartnerRemarkUpdateRequest;
import com.couple.service.user.dto.UserBindRequest;
import com.couple.service.user.dto.UserLoginRequest;
import com.couple.service.user.dto.UserProfileUpdateRequest;
import com.couple.service.user.dto.UserRegisterRequest;
import com.couple.service.user.dto.UserUnbindRequest;
import com.couple.service.user.vo.EntitlementsResponse;
import com.couple.service.user.vo.CoupleBindRequestView;
import com.couple.service.user.vo.PartnerRemarkResponse;
import com.couple.service.user.vo.UserBindResponse;
import com.couple.service.user.vo.UserLoginResponse;
import com.couple.service.user.vo.UserProfileResponse;
import com.couple.service.user.vo.UserProfileUpdateResponse;
import com.couple.service.user.vo.UserRegisterResponse;

import java.util.List;

/** 用户账户与伴侣关系业务。 */
public interface UserService {

    UserRegisterResponse register(UserRegisterRequest request);

    UserLoginResponse login(UserLoginRequest request);

    void logout(CurrentUser currentUser);

    CoupleBindRequestView createBindRequest(Long userId, UserBindRequest request);

    List<CoupleBindRequestView> getReceivedBindRequests(Long userId);

    List<CoupleBindRequestView> getSentBindRequests(Long userId);

    UserBindResponse acceptBindRequest(Long userId, Long requestId);

    CoupleBindRequestView rejectBindRequest(Long userId, Long requestId);

    UserProfileResponse getProfile(Long userId);

    UserProfileUpdateResponse updateProfile(Long userId, UserProfileUpdateRequest request);

    PartnerRemarkResponse updatePartnerRemark(Long userId, PartnerRemarkUpdateRequest request);

    EntitlementsResponse getEntitlements(Long userId);

    void unbind(Long userId, UserUnbindRequest request);
}

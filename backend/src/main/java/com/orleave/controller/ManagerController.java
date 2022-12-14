package com.orleave.controller;

import java.util.NoSuchElementException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.orleave.auth.SsafyUserDetails;
import com.orleave.dto.InquiryDetailDto;
import com.orleave.dto.InquiryListDto;
import com.orleave.dto.ReportDetailDto;
import com.orleave.dto.UserListDto;
import com.orleave.dto.UserReportListDto;
import com.orleave.dto.request.InquiryAnswerRequestDto;
import com.orleave.dto.request.LoginRequestDto;
import com.orleave.dto.request.NicknameModifyRequestDto;
import com.orleave.dto.request.NoticeModifyRequestDto;
import com.orleave.dto.request.NoticeRequestDto;
import com.orleave.dto.request.UserNoRequestDto;
import com.orleave.dto.response.BaseResponseDto;
import com.orleave.dto.response.InquiryDetailResponseDto;
import com.orleave.dto.response.InquiryListResponseDto;
import com.orleave.dto.response.LoginResponseDto;
import com.orleave.dto.response.ReportDetailResponseDto;
import com.orleave.dto.response.UserListResponseDto;
import com.orleave.dto.response.UserReportListResponseDto;
import com.orleave.entity.User;
import com.orleave.exception.UserNotFoundException;
import com.orleave.service.ManagerService;
import com.orleave.service.UserService;
import com.orleave.util.JwtTokenUtil;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import springfox.documentation.annotations.ApiIgnore;

@Api(value = "관리자 API", tags = {"Manager"})
@RestController
@RequestMapping("/api/v1/manager")
public class ManagerController {
	
	@Autowired
	UserService userService;
	
	@Autowired
	ManagerService managerService;
	
	@Autowired
	PasswordEncoder passwordEncoder;
	
	@PostMapping("/login")
	@ApiOperation(value = "로그인", notes = "<strong>아이디와 패스워드</strong>를 통해 로그인 한다.") 
    @ApiResponses({
        @ApiResponse(code = 200, message = "성공", response = LoginResponseDto.class),
        @ApiResponse(code = 401, message = "인증 실패", response = BaseResponseDto.class),
        @ApiResponse(code = 403, message = "로그인 횟수 초과", response = BaseResponseDto.class),
        @ApiResponse(code = 404, message = "사용자 없음", response = BaseResponseDto.class),
        @ApiResponse(code = 500, message = "서버 오류", response = BaseResponseDto.class)
    })
	public ResponseEntity<LoginResponseDto> login(
			@RequestBody @ApiParam(value="로그인 정보", required = true) LoginRequestDto loginInfo) throws Exception {
		String email = loginInfo.getEmail();
		String password = loginInfo.getPassword();
		User user;
		
		try {
			user = userService.getUserByEmail(email);
		} catch (NoSuchElementException e) {
			return ResponseEntity.status(404).body(LoginResponseDto.of(404, "Invalid Email", null));
		}
		
		userService.logincheck(user.getNo());
		
		String userNo = Integer.toString(user.getNo());
		String userType = user.getUserType();
		int imageNo = user.getImageNo();
		String nickname=user.getNickname();
		String gender=user.getGender();
		// 로그인 요청한 유저로부터 입력된 패스워드 와 디비에 저장된 유저의 암호화된 패스워드가 같은지 확인.(유효한 패스워드인지 여부 확인)
		if(passwordEncoder.matches(password, user.getPassword())) {
			if (!userType.equals("MANAGER")) {
				return ResponseEntity.status(403).body(LoginResponseDto.of(403, "Not Manager", null));
			}
			// 유효한 패스워드가 맞는 경우, 로그인 성공으로 응답.(액세스 토큰을 포함하여 응답값 전달)
			return ResponseEntity.ok(LoginResponseDto.of(200, "Success", JwtTokenUtil.getToken(userNo,userType,imageNo,nickname,gender)));
		}
		userService.loginfailed(user.getNo());
		return ResponseEntity.status(401).body(LoginResponseDto.of(401, "Invalid Password", null));
	}

	@GetMapping("/users")
	@ApiOperation(value = "유저 목록 전체 조회", notes = "유저 목록을 페이지 정보에 따라 전체 조회한다.") 
    @ApiResponses({
        @ApiResponse(code = 200, message = "성공"),
        @ApiResponse(code = 401, message = "인증되지 않은 토큰"),
        @ApiResponse(code = 403, message = "권한이 없는 사용자"),
        @ApiResponse(code = 500, message = "서버 오류")
    })
	public ResponseEntity<UserListResponseDto> getAllUsers(@RequestParam("page") int page, @RequestParam("size") int size
			,@ApiIgnore Authentication authentication) {
		if (authentication == null) return ResponseEntity.status(401).body(UserListResponseDto.of(401, "Unauthorized",null));
		SsafyUserDetails userDetails = (SsafyUserDetails)authentication.getDetails();
		String UserType = userDetails.getUser().getUserType();
		if(!UserType.equals("MANAGER"))return ResponseEntity.status(403).body(UserListResponseDto.of(403, "Not Manager", null));
		
		Page<UserListDto> userList = managerService.getUsers(PageRequest.of(page, size, Sort.by("no").descending()));
		return ResponseEntity.status(200).body(UserListResponseDto.of(200, "Success", userList));
	}
	
	@GetMapping("/users/{no}")
	@ApiOperation(value = "유저 신고 목록 조회", notes = "선택한 유저의 신고 목록을 조회한다.") 
    @ApiResponses({
        @ApiResponse(code = 200, message = "성공"),
        @ApiResponse(code = 401, message = "인증되지 않은 토큰"),
        @ApiResponse(code = 403, message = "권한이 없는 사용자"),
        @ApiResponse(code = 404, message = "신고 목록 조회 실패"),
        @ApiResponse(code = 500, message = "서버 오류")
    })
	public ResponseEntity<UserReportListResponseDto> getUserReportList(@RequestParam("page") int page, @RequestParam("size") int size,@PathVariable("no") int no
			,@ApiIgnore Authentication authentication) {
		if (authentication == null) return ResponseEntity.status(401).body(UserReportListResponseDto.of(401, "Unauthorized",null));
		SsafyUserDetails userDetails = (SsafyUserDetails)authentication.getDetails();
		String UserType = userDetails.getUser().getUserType();
		if(!UserType.equals("MANAGER"))return ResponseEntity.status(403).body(UserReportListResponseDto.of(403, "Not Manager", null));
		
		Page<UserReportListDto> userReportList = managerService.getUserReports(PageRequest.of(page, size, Sort.by("no").descending()),no);
		return ResponseEntity.status(200).body(UserReportListResponseDto.of(200, "Success", userReportList));
	}
	
	@GetMapping("/reports/{report_no}")
	@ApiOperation(value = "신고 목록  상세 조회", notes = "선택한 신고의 내용을 조회한다.") 
    @ApiResponses({
        @ApiResponse(code = 200, message = "성공"),
        @ApiResponse(code = 401, message = "인증되지 않은 토큰"),
        @ApiResponse(code = 403, message = "권한이 없는 사용자"),
        @ApiResponse(code = 404, message = "신고  조회 실패"),
        @ApiResponse(code = 500, message = "서버 오류")
    })
	public ResponseEntity<? extends BaseResponseDto> getReportDetail(@PathVariable("report_no") int no
			,@ApiIgnore Authentication authentication) throws Exception {
		if (authentication == null) return ResponseEntity.status(401).body(BaseResponseDto.of(401, "Unauthorized"));
		SsafyUserDetails userDetails = (SsafyUserDetails)authentication.getDetails();
		String UserType = userDetails.getUser().getUserType();
		if(!UserType.equals("MANAGER"))return ResponseEntity.status(403).body(BaseResponseDto.of(403, "Not Manager"));
		
		ReportDetailDto report = managerService.getReportDetail(no);
		if (report == null) return ResponseEntity.status(404).body(BaseResponseDto.of(404, "Not Found"));
		return ResponseEntity.status(200).body(ReportDetailResponseDto.of(200, "Success", report));
	}
	
	@PutMapping("/ban")
	@ApiOperation(value = "선택 유저 정지", notes = "선택한 유저를 정지시킨다.") 
    @ApiResponses({
        @ApiResponse(code = 200, message = "성공"),
        @ApiResponse(code = 401, message = "인증되지 않은 토큰"),
        @ApiResponse(code = 403, message = "권한이 없는 사용자"),
        @ApiResponse(code = 404, message = "정지 실패"),
        @ApiResponse(code = 500, message = "서버 오류")
    })
	public ResponseEntity<? extends BaseResponseDto> BanUser(
			@RequestBody @ApiParam(value="유저", required = true)UserNoRequestDto userNoRequestDto
			,@ApiIgnore Authentication authentication) throws Exception {
		if (authentication == null) return ResponseEntity.status(401).body(BaseResponseDto.of(401, "Unauthorized"));
		
		SsafyUserDetails userDetails = (SsafyUserDetails)authentication.getDetails();
		String UserType = userDetails.getUser().getUserType();
		if(!UserType.equals("MANAGER"))return ResponseEntity.status(403).body(BaseResponseDto.of(403, "Not Manager"));
		
		managerService.BanUser(userNoRequestDto.getNo());
		return ResponseEntity.status(200).body(BaseResponseDto.of(200, "Success"));
	}
	
	@PutMapping("/nickname")
	@ApiOperation(value = "닉네임 변경", notes = "선택한 유저의 닉네임을 변경한다.") 
    @ApiResponses({
        @ApiResponse(code = 200, message = "성공"),
        @ApiResponse(code = 401, message = "인증되지 않은 토큰"),
        @ApiResponse(code = 403, message = "권한이 없는 사용자"),
        @ApiResponse(code = 404, message = "정지 실패"),
        @ApiResponse(code = 500, message = "서버 오류")
    })
	public ResponseEntity<? extends BaseResponseDto> ModifyNickname(
			@RequestBody @ApiParam(value="유저 정보", required = true)NicknameModifyRequestDto nicknameModifyRequestDto
			,@ApiIgnore Authentication authentication) throws Exception {
		if (authentication == null) return ResponseEntity.status(401).body(BaseResponseDto.of(401, "Unauthorized"));
		
		SsafyUserDetails userDetails = (SsafyUserDetails)authentication.getDetails();
		String UserType = userDetails.getUser().getUserType();
		if(!UserType.equals("MANAGER"))return ResponseEntity.status(403).body(BaseResponseDto.of(403, "Not Manager"));
		try {
			userService.getUserByNickname(nicknameModifyRequestDto.getNickname());
			return ResponseEntity.status(400).body(BaseResponseDto.of(400, "Duplicate Nickname"));
		} catch (UserNotFoundException e) {
			managerService.ModifyNickname(nicknameModifyRequestDto);
			return ResponseEntity.status(200).body(BaseResponseDto.of(200, "Success"));
		}
	}
	
	@PutMapping("/inquiry/answer")
	@ApiOperation(value = "문의 답변", notes = "선택한 문의에 답변을 남긴다.") 
    @ApiResponses({
        @ApiResponse(code = 200, message = "성공"),
        @ApiResponse(code = 401, message = "인증되지 않은 토큰"),
        @ApiResponse(code = 403, message = "권한이 없는 사용자"),
        @ApiResponse(code = 404, message = "실패"),
        @ApiResponse(code = 500, message = "서버 오류")
    })
	public ResponseEntity<? extends BaseResponseDto> InquiryAnswer(
			@RequestBody @ApiParam(value="문의 답변", required = true)InquiryAnswerRequestDto inquiryAnswerRequestDto
			,@ApiIgnore Authentication authentication) throws Exception {
		if (authentication == null) return ResponseEntity.status(401).body(BaseResponseDto.of(401, "Unauthorized"));
		System.out.println("asdf");
		SsafyUserDetails userDetails = (SsafyUserDetails)authentication.getDetails();
		String UserType = userDetails.getUser().getUserType();
		if(!UserType.equals("MANAGER"))return ResponseEntity.status(403).body(BaseResponseDto.of(403, "Not Manager"));
		
		
		managerService.InquiryAnswer(inquiryAnswerRequestDto);
		return ResponseEntity.status(200).body(BaseResponseDto.of(200, "Success"));
	}
	
	@GetMapping("/inquiries")
	@ApiOperation(value = "문의 목록", notes = "문의 목록을 출력합니다.") 
    @ApiResponses({
        @ApiResponse(code = 200, message = "성공"),
        @ApiResponse(code = 401, message = "인증되지 않은 토큰"),
        @ApiResponse(code = 403, message = "권한이 없는 사용자"),
        @ApiResponse(code = 404, message = "실패"),
        @ApiResponse(code = 500, message = "서버 오류")
    })
	public ResponseEntity<? extends BaseResponseDto> InquiryList(@RequestParam("page") int page, @RequestParam("size") int size,
			@ApiIgnore Authentication authentication) throws Exception {
		if (authentication == null) return ResponseEntity.status(401).body(BaseResponseDto.of(401, "Unauthorized"));
		
		SsafyUserDetails userDetails = (SsafyUserDetails)authentication.getDetails();
		String UserType = userDetails.getUser().getUserType();
		if(!UserType.equals("MANAGER"))return ResponseEntity.status(403).body(BaseResponseDto.of(403, "Not Manager"));
		
		Page<InquiryListDto> inquiryList = managerService.getInquiries(PageRequest.of(page, size, Sort.by("no").descending()));
		return ResponseEntity.status(200).body(InquiryListResponseDto.of(200, "Success", inquiryList));
	}
	
	@GetMapping("/inquiries/{no}")
	@ApiOperation(value = "1:1문의 상세 조회", notes = "1:1문의 상세 정보를 조회한다.") 
    @ApiResponses({
        @ApiResponse(code = 200, message = "성공"),
        @ApiResponse(code = 401, message = "인증되지 않은 토큰"),
        @ApiResponse(code = 403, message = "접근 권한 없음"),
        @ApiResponse(code = 404, message = "1:1문의 조회 실패"),
        @ApiResponse(code = 500, message = "서버 오류")
    })
	public ResponseEntity<? extends BaseResponseDto> getInquiryDetail(
			@ApiIgnore Authentication authentication, @PathVariable("no") int no) throws Exception {
		if (authentication == null) return ResponseEntity.status(401).body(BaseResponseDto.of(401, "Unauthorized"));
		SsafyUserDetails userDetails = (SsafyUserDetails)authentication.getDetails();
		InquiryDetailDto inquiry = managerService.getInquiryDetail(no);
		return ResponseEntity.status(200).body(InquiryDetailResponseDto.of(200, "Success", inquiry));
	}
	
	@PostMapping("/notices")
	@ApiOperation(value="공지사항 작성",notes="새로운 공지사항을 작성한다.")
	@ApiResponses({
		@ApiResponse(code = 200, message="성공"),
        @ApiResponse(code = 401, message = "인증되지 않은 토큰"),
		@ApiResponse(code = 403, message="권한이 없는 사용자"),
		@ApiResponse(code = 404, message="실패"),
        @ApiResponse(code = 500, message = "서버 오류")
	})
	public ResponseEntity<? extends BaseResponseDto> CreateNotices(@RequestBody @ApiParam(value="공지 작성",required=true) NoticeRequestDto noticeRequestDto
			,@ApiIgnore Authentication authentication) throws Exception {
		if (authentication == null) return ResponseEntity.status(401).body(BaseResponseDto.of(401, "Unauthorized"));
		
		SsafyUserDetails userDetails = (SsafyUserDetails)authentication.getDetails();
		String UserType = userDetails.getUser().getUserType();
		if(!UserType.equals("MANAGER"))return ResponseEntity.status(403).body(BaseResponseDto.of(403, "Not Manager"));
		
		managerService.CreateNotices(noticeRequestDto);
		return ResponseEntity.status(200).body(BaseResponseDto.of(200, "Success"));
		
	}
	
	@PutMapping("/notices")
	@ApiOperation(value = "공지사항 수정", notes = "선택한 공지를 수정한다.") 
    @ApiResponses({
        @ApiResponse(code = 200, message = "성공"),
        @ApiResponse(code = 401, message = "인증되지 않은 토큰"),
        @ApiResponse(code = 403, message = "권한이 없는 사용자"),
        @ApiResponse(code = 404, message = "실패"),
        @ApiResponse(code = 500, message = "서버 오류")
    })
	public ResponseEntity<? extends BaseResponseDto> ModifyNotices(@RequestBody @ApiParam(value="공지 작성",required=true) NoticeModifyRequestDto noticeModifyRequestDto
			,@ApiIgnore Authentication authentication) throws Exception {
		if (authentication == null) return ResponseEntity.status(401).body(BaseResponseDto.of(401, "Unauthorized"));
		
		SsafyUserDetails userDetails = (SsafyUserDetails)authentication.getDetails();
		String UserType = userDetails.getUser().getUserType();
		if(!UserType.equals("MANAGER"))return ResponseEntity.status(403).body(BaseResponseDto.of(403, "Not Manager"));
		
		managerService.ModifyNotices(noticeModifyRequestDto);
		return ResponseEntity.status(200).body(BaseResponseDto.of(200, "Success"));
	}
	
	@DeleteMapping("/notices/{notice_no}")
	@ApiOperation(value = "공지사항 삭제", notes = "선택한 공지를 수정한다.") 
    @ApiResponses({
        @ApiResponse(code = 200, message = "성공"),
        @ApiResponse(code = 401, message = "인증되지 않은 토큰"),
        @ApiResponse(code = 403, message = "권한이 없는 사용자"),
        @ApiResponse(code = 404, message = "실패"),
        @ApiResponse(code = 500, message = "서버 오류")
    })
	public ResponseEntity<? extends BaseResponseDto> DeleteNotices(@PathVariable("notice_no") int no,
			@ApiIgnore Authentication authentication) throws Exception {
		if (authentication == null) return ResponseEntity.status(401).body(BaseResponseDto.of(401, "Unauthorized"));
		
		SsafyUserDetails userDetails = (SsafyUserDetails)authentication.getDetails();
		String UserType = userDetails.getUser().getUserType();
		if(!UserType.equals("MANAGER"))return ResponseEntity.status(403).body(BaseResponseDto.of(403, "Not Manager"));
		
		managerService.DeleteNotices(no);
		return ResponseEntity.status(200).body(BaseResponseDto.of(200, "Success"));
	}
	
}


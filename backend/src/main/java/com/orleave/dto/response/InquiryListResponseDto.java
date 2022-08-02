package com.orleave.dto.response;

import java.util.List;

import org.springframework.data.domain.Page;

import com.orleave.entity.Inquiry;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ApiModel("InquiryListResponse")
public class InquiryListResponseDto extends BaseResponseDto {
	
	@ApiModelProperty(name="Inquiry List")
	Page<Inquiry> InquiryList;
	
	public static InquiryListResponseDto of(Integer statusCode, String message, Page<Inquiry> inquiryList) {
		InquiryListResponseDto res = new InquiryListResponseDto();
		res.setStatusCode(statusCode);
		res.setMessage(message);
		res.setInquiryList(inquiryList);
		return res;
	}
}

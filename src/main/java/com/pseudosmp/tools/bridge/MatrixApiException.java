package com.pseudosmp.tools.bridge;

import java.io.IOException;

public class MatrixApiException extends IOException {
	private final int statusCode;
	private final String errcode;
	private final String rawError;
	private final String userSolution;

	public MatrixApiException(int statusCode, String errcode, String rawError, String userSolution, String message) {
		super(message);
		this.statusCode = statusCode;
		this.errcode = errcode;
		this.rawError = rawError;
		this.userSolution = userSolution;
	}

	public int getStatusCode() {
		return statusCode;
	}

	public String getErrcode() {
		return errcode;
	}

	public String getRawError() {
		return rawError;
	}

	public String getUserSolution() {
		return userSolution;
	}
}

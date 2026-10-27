package com.example.demo.ledger;

/** 요청 값이 잘못되었음을 알리는 예외. 가계부 API에서 400 응답으로 변환된다. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}

package com.example.email.writer.app;

public class EmailResponse {
    private String content;

    public EmailResponse() {}

    public EmailResponse(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}

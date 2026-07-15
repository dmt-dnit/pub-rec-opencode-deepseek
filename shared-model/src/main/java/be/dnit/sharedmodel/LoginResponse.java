package be.dnit.sharedmodel;

public record LoginResponse(String token, String email, String name, String role) {}

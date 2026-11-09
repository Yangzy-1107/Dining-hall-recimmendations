package cn.edu.canteen;

import java.io.Serializable;

public record UserSession(long id, String username, String displayName, String role) implements Serializable {}

package org.acme.registration;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Simple bean representing data hold into CLoudEvent.
 * @author laurent
 */
@RegisterForReflection
public class UserRegistration {
    
    private String id;
    private String fullName;
    private String email;
    private int age;
    private String sendAt;

    public UserRegistration() {
    }

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }

    public int getAge() {
        return age;
    }
    public void setAge(int age) {
        this.age = age;
    }

    public String getSendAt() {
        return sendAt;
    }
    public void setSendAt(String sendAt) {
        this.sendAt = sendAt;
    }
}

package com.moapan.identity.api;

import com.moapan.identity.application.EmailIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
public class EmailController {
    record SignupCode(@NotBlank @Size(max=128)String username,@NotBlank @Size(max=254)String email,@NotNull @AssertTrue Boolean emailPolicyAccepted,@NotBlank @Size(max=32) String emailPolicyVersion) {}
    record ResetCode(@NotBlank @Size(max=128)String username,@NotBlank @Size(max=254)String email) {}
    record Reset(@NotBlank @Size(max=128)String username,@NotNull UUID challengeId,@NotBlank @Pattern(regexp="[0-9]{6}")String code,@NotNull @Size(max=256)String newPassword) {}
    record BindCode(@NotNull @Size(max=256)String currentPassword,@NotBlank @Size(max=254)String email,@NotNull @AssertTrue Boolean emailPolicyAccepted,@NotBlank @Size(max=32) String emailPolicyVersion) {}
    record Bind(@NotNull UUID challengeId,@NotBlank @Pattern(regexp="[0-9]{6}")String code,@NotNull @AssertTrue Boolean emailPolicyAccepted,@NotBlank @Size(max=32) String emailPolicyVersion) {}
    private final EmailIdentity service;
    public EmailController(EmailIdentity service){this.service=service;}
    @PostMapping("/api/v1/auth/email/signup-code") @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
    EmailIdentity.Issued signupCode(@Valid @RequestBody SignupCode input,HttpServletRequest request){return service.requestSignup(input.username(),input.email(),binding(request),input.emailPolicyAccepted(),input.emailPolicyVersion());}
    @PostMapping("/api/v1/auth/password/reset-code") @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
    EmailIdentity.Issued resetCode(@Valid @RequestBody ResetCode input,HttpServletRequest request){return service.requestReset(input.username(),input.email(),binding(request));}
    @PostMapping("/api/v1/auth/password/reset") void reset(@Valid @RequestBody Reset input,HttpServletRequest request){service.reset(input.username(),input.newPassword(),input.challengeId(),input.code(),binding(request));if(request.getSession(false)!=null)request.getSession(false).invalidate();}
    @GetMapping("/api/v1/account/email") EmailIdentity.Profile profile(Authentication actor){return service.profile(UUID.fromString(actor.getName()));}
    @PostMapping("/api/v1/account/email/code") @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
    EmailIdentity.Issued bindCode(@Valid @RequestBody BindCode input,Authentication actor,HttpServletRequest request){return service.requestBinding(UUID.fromString(actor.getName()),input.currentPassword(),input.email(),binding(request),input.emailPolicyAccepted(),input.emailPolicyVersion());}
    @PutMapping("/api/v1/account/email") void bind(@Valid @RequestBody Bind input,Authentication actor,HttpServletRequest request){service.bind(UUID.fromString(actor.getName()),input.challengeId(),input.code(),binding(request),input.emailPolicyAccepted(),input.emailPolicyVersion());if(request.getSession(false)!=null)request.getSession(false).invalidate();}
    static String binding(HttpServletRequest request){var session=request.getSession();synchronized(session){Object existing=session.getAttribute("email.challenge.binding");if(existing==null){existing=UUID.randomUUID().toString();session.setAttribute("email.challenge.binding",existing);}return existing.toString();}}
}

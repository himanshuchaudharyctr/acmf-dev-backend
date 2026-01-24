package com.itstrat.acmf.apis.controller;

import com.itstrat.acmf.apis.Request.LoginRequest;
import com.itstrat.acmf.apis.Response.AuthResponse;
import com.itstrat.acmf.apis.config.JwtGenerator;
import com.itstrat.acmf.apis.entity.User;
import com.itstrat.acmf.apis.repository.UserRepository;
import com.itstrat.acmf.apis.service.CustomUserDetailsImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "User Authentication", description = "Authenticate user APIs")
@RestController
@RequestMapping("/auth")
public class UserAuthentication {

	/*@Autowired
	private CredentialService service;

	@PostMapping("/login")
	@Operation(
		      summary = "Authenticate user.",
		      description = "Authenticate user using basic authentication mechanism.",
		      tags = {}) //"login",
		  @ApiResponses({
		      @ApiResponse(responseCode = "200", content = { @Content(schema = @Schema()) }),
		      @ApiResponse(responseCode = "404", content = { @Content(schema = @Schema()) }),
		      @ApiResponse(responseCode = "500", content = { @Content(schema = @Schema()) }) })
	@SecurityRequirements()
	public ResponseEntity<String> login(@RequestBody Map<String, String> credentials) {
		String username = credentials.get("username");
		String password = credentials.get("password");

		boolean isValidUser = service.validateCredentials(username, password);

		if (isValidUser) {
			return ResponseEntity.ok("Login successful");
		} else {
			return ResponseEntity.status(401).body("Invalid credentials");
		}
	}

	@GetMapping
	@SecurityRequirements()
    public ResponseEntity<Authentication> index(Model model, Authentication user) {

        return ResponseEntity.ok().body(user);
    }*/

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private CustomUserDetailsImpl customUserDetails;

	@PostMapping("/signin")
	public ResponseEntity<AuthResponse>createUserHandler(@RequestBody User user) throws Exception{

		User isUserExist = userRepository.findByEmail(user.getEmail());

		if(isUserExist != null)
		{
			throw  new Exception("username already exists");
		}

		User createdUser = new User();
		createdUser.setPassword(passwordEncoder.encode(user.getPassword()));
		createdUser.setEmail(user.getEmail());
		createdUser.setFirstName(user.getFirstName());
		createdUser.setLastName(user.getLastName());

		User savedUser = userRepository.save(createdUser);
		Authentication authentication = new UsernamePasswordAuthenticationToken(user.getEmail(), user.getPassword());
		SecurityContextHolder.getContext().setAuthentication(authentication);

		String jwt = JwtGenerator.generateToken(authentication);
		AuthResponse res = new AuthResponse();
		res.setMessage("signup successfull");
		res.setJwt(jwt);

		return  new ResponseEntity<>(res, HttpStatus.CREATED);
	}

	@PostMapping("/login")
	public ResponseEntity<AuthResponse> loginUserHandler(@RequestBody LoginRequest loginRequest) throws Exception {
		String email = loginRequest.getEmail();
		String password = loginRequest.getPassword();

		Authentication authentication = authenticate(email, password);
		SecurityContextHolder.getContext().setAuthentication(authentication);

		String jwt = JwtGenerator.generateToken(authentication);
		AuthResponse res = new AuthResponse();
		res.setMessage("signin successful");
		res.setJwt(jwt);
		return new ResponseEntity<>(res, HttpStatus.OK);
	}


	private Authentication authenticate(String email, String password) {
		UserDetails userDetails = customUserDetails.loadUserByUsername(email);

		System.out.println("sign in userDetails - " + userDetails);

		if (userDetails == null) {
			System.out.println("sign in userDetails - null " + userDetails);
			throw new BadCredentialsException("Invalid email or password");
		}
		if (!passwordEncoder.matches(password, userDetails.getPassword())) {
			System.out.println("sign in userDetails - password not match " + userDetails);
			throw new BadCredentialsException("Invalid email or password");
		}
		return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
	}
}

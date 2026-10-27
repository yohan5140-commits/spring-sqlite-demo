package com.example.demo.user;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.PageableDefault;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

    private static final int SQLITE_CONSTRAINT = 19;
    private static final Set<String> SORTABLE_PROPERTIES = Set.of("id", "name", "email", "createdAt");

    public record UserRequest(
            @NotBlank(message = "이름은 필수입니다.") String name,
            @NotBlank(message = "이메일은 필수입니다.") @Email(message = "이메일 형식이 올바르지 않습니다.") String email) {
    }

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    public PagedModel<UserResponse> list(@PageableDefault(size = 10, sort = "id") Pageable pageable) {
        // 정렬 프로퍼티는 그대로 SQL의 컬럼명이 되므로 허용된 값만 통과시킨다
        pageable.getSort().forEach(order -> {
            if (!SORTABLE_PROPERTIES.contains(order.getProperty())) {
                throw new IllegalArgumentException(
                        "정렬할 수 없는 항목입니다: " + order.getProperty() + " (가능: " + SORTABLE_PROPERTIES + ")");
            }
        });
        return new PagedModel<>(userRepository.findAll(withIdTieBreaker(pageable)).map(UserResponse::from));
    }

    /** 정렬 값이 같은 행의 순서가 바뀌어 페이지 사이에 중복·누락이 생기지 않도록 id를 보조 정렬 기준으로 붙인다. */
    private static Pageable withIdTieBreaker(Pageable pageable) {
        if (pageable.getSort().getOrderFor("id") != null) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort().and(Sort.by("id")));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> get(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(user -> ResponseEntity.ok(UserResponse.from(user)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody UserRequest request) {
        return UserResponse.from(userRepository.save(User.create(request.name(), request.email())));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> update(@PathVariable Long id, @Valid @RequestBody UserRequest request) {
        return userRepository.findById(id)
                .map(user -> {
                    user.update(request.name(), request.email());
                    return ResponseEntity.ok(UserResponse.from(userRepository.save(user)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        userRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBadRequest(IllegalArgumentException e) {
        return e.getMessage();
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return errors;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleConflict() {
        return "이미 존재하는 이메일이거나 필수 값이 누락되었습니다.";
    }

    // SQLite 드라이버는 제약조건 위반을 SQLState 없이 error code 19(SQLITE_CONSTRAINT)로만 알려줘서
    // Spring이 DataIntegrityViolationException으로 변환하지 못한다
    @ExceptionHandler(UncategorizedSQLException.class)
    public ResponseEntity<String> handleSqlite(UncategorizedSQLException e) {
        if (e.getSQLException().getErrorCode() == SQLITE_CONSTRAINT) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(handleConflict());
        }
        throw e;
    }
}

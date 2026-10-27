package com.example.demo.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private static final int SQLITE_CONSTRAINT = 19;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserController userController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    /** DB에서 읽어온 것처럼 id와 UTC 생성 시각이 채워진 User를 만든다. */
    private static User savedUser(long id, String name, String email) {
        User user = User.create(name, email);
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "createdAt", "2026-10-27 06:32:38");
        return user;
    }

    @Test
    void 목록_조회는_생성_시각을_KST로_변환한다() throws Exception {
        Page<User> page = new PageImpl<>(List.of(savedUser(1, "김철수", "chulsoo@example.com")), PageRequest.of(0, 10), 1);
        when(userRepository.findAll(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].name").value("김철수"))
                .andExpect(jsonPath("$.content[0].createdAt").value("2026-10-27 15:32:38"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void 기본_페이징은_id_오름차순_10건() throws Exception {
        when(userRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        mockMvc.perform(get("/users")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAll(captor.capture());
        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(captor.getValue().getPageSize()).isEqualTo(10);
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by("id"));
    }

    @Test
    void 페이지_크기_정렬_파라미터가_리포지토리에_전달된다() throws Exception {
        when(userRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        mockMvc.perform(get("/users?page=2&size=5&sort=name,desc")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAll(captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(5);
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by(Sort.Order.desc("name"), Sort.Order.asc("id")));
    }

    @Test
    void id로_정렬하면_보조_정렬을_중복해서_붙이지_않는다() throws Exception {
        when(userRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        mockMvc.perform(get("/users?sort=id,desc")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAll(captor.capture());
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by(Sort.Order.desc("id")));
    }

    @Test
    void 허용되지_않은_정렬_항목은_400() throws Exception {
        mockMvc.perform(get("/users?sort=password,asc"))
                .andExpect(status().isBadRequest());

        verify(userRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void 단건_조회() throws Exception {
        when(userRepository.findById(1L)).thenReturn(Optional.of(savedUser(1, "김철수", "chulsoo@example.com")));

        mockMvc.perform(get("/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("chulsoo@example.com"));
    }

    @Test
    void 없는_사용자_조회는_404() throws Exception {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/users/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 사용자_생성은_201() throws Exception {
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 6L);
            return user;
        });

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"홍길동\",\"email\":\"gildong@example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(6))
                .andExpect(jsonPath("$.name").value("홍길동"))
                .andExpect(jsonPath("$.email").value("gildong@example.com"));
    }

    @Test
    void 잘못된_이메일_형식은_400() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"홍길동\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.email").value("이메일 형식이 올바르지 않습니다."));

        verify(userRepository, never()).save(any());
    }

    @Test
    void 이름이_비었고_이메일이_없으면_필드별_메시지와_함께_400() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").value("이름은 필수입니다."))
                .andExpect(jsonPath("$.email").value("이메일은 필수입니다."));

        verify(userRepository, never()).save(any());
    }

    @Test
    void 이메일이_중복되면_409() throws Exception {
        when(userRepository.save(any(User.class))).thenThrow(new UncategorizedSQLException(
                "PreparedStatementCallback", "INSERT INTO users ...",
                new SQLException("UNIQUE constraint failed: users.email", null, SQLITE_CONSTRAINT)));

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"중복\",\"email\":\"chulsoo@example.com\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void 사용자_수정() throws Exception {
        when(userRepository.findById(1L)).thenReturn(Optional.of(savedUser(1, "김철수", "chulsoo@example.com")));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(put("/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"김영수\",\"email\":\"youngsoo@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("김영수"))
                .andExpect(jsonPath("$.email").value("youngsoo@example.com"));
    }

    @Test
    void 없는_사용자_수정은_404() throws Exception {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/users/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"김영수\",\"email\":\"youngsoo@example.com\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 수정할_때도_이메일_형식을_검증한다() throws Exception {
        mockMvc.perform(put("/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"김철수\",\"email\":\"bad@\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.email").value("이메일 형식이 올바르지 않습니다."));
    }

    @Test
    void 사용자_삭제는_204() throws Exception {
        when(userRepository.existsById(1L)).thenReturn(true);

        mockMvc.perform(delete("/users/1"))
                .andExpect(status().isNoContent());

        verify(userRepository).deleteById(1L);
    }

    @Test
    void 없는_사용자_삭제는_404() throws Exception {
        when(userRepository.existsById(99L)).thenReturn(false);

        mockMvc.perform(delete("/users/99"))
                .andExpect(status().isNotFound());

        verify(userRepository, never()).deleteById(any());
    }
}

package ru.netcracker.registration.controller;

import org.joda.time.format.DateTimeFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.netcracker.registration.security.TokenUtils;
import ru.netcracker.registration.mail.burningLinks.BurningLinksManager;
import ru.netcracker.registration.mail.mailer.GmailSender;
import ru.netcracker.registration.model.DTO.UserDTO;
import ru.netcracker.registration.model.UserGroup;
import ru.netcracker.registration.security.service.SecurityService;
import ru.netcracker.registration.service.UserGroupService;
import ru.netcracker.registration.service.UserService;

import org.joda.time.*;

import javax.servlet.http.HttpServletRequest;

/**
 * Контроллер, предоставляет набор REST методов, с помощью которых можно работать с базой пользователей
 * К каждому методу привязан URL адрес, по которому и осуществляется REST запрос
 */
@RestController
@RequestMapping("/userapi")
public class UserController {
    private final UserService userService;
    @Autowired
    private UserGroupService groupService;
    @Autowired
    private SecurityService securityService;


    public UserController(UserService service) {
        userService = service;
    }


    @GetMapping("/testName")
    public ResponseEntity<?> getName() {
        try {
            return new ResponseEntity<Object>(
                    securityService.findLoggedInEmail(),
                    HttpStatus.OK
            );
        } catch (Exception e) {
            return new ResponseEntity<Object>(
                    e.getMessage(),
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    @GetMapping("/get/loggedIn")
    public ResponseEntity<?> getLoggedIn() {
        try {
            return new ResponseEntity<Object>(
                    userService.get(securityService.findLoggedInEmail()),
                    HttpStatus.OK
            );
        } catch (Exception e) {
            return new ResponseEntity<Object>(
                    e.getMessage(),
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    /**
     * Вернёт в json формате пользователя с указанным ID
     *
     * @param id - ID пользователя
     * @return Запрашиваемый пользователь
     */
    @GetMapping("/get/byID/{id}")
    public @ResponseBody
    ResponseEntity<?> get(@PathVariable long id) {
        try {
            UserDTO userDTO = userService.get(id);
            return new ResponseEntity<>(userDTO, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(
                    e.getMessage(),
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    /**
     * Вернёт в json формате пользователя с указанным email
     *
     * @param email - Email пользователя
     * @return Запрашиваемый пользователь
     */
    @GetMapping("/get/byEmail/{email:.+}")
    public @ResponseBody
    ResponseEntity<?> get(@PathVariable String email) {
        try {
            UserDTO userDTO = userService.get(email);
            return new ResponseEntity<>(userDTO, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(
                    e.getMessage(),
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    /**
     * Вернёт в json формате массив всех пользователей
     *
     * @return Массив всех пользователей
     */
    @GetMapping("/get/all")
    public @ResponseBody
    Iterable<UserDTO> getAll() {
        return userService.getAll();
    }

    /**
     * Добавить нового пользователя
     * Тело запроса в формате Json содержит информацию о пользователе
     *
     * @param userDTO Json автоматически распарсится и станет объектом userDTO
     * @return Вернёт только что созданного пользователя
     */
    @PostMapping("/add")
    public ResponseEntity<?> post(@RequestBody UserDTO userDTO) {
        try {
            userDTO.setRegistrationDate(LocalDate.now().toString(DateTimeFormat.forPattern("d-M-YYYY")));
            userDTO.setGroupID(groupService.get("Unconfirmed"));
            userService.add(userDTO);
            String token= securityService.login(userDTO.getEmail(), userDTO.getPassword());

            GmailSender sender = new GmailSender("netcracker.training.center@gmail.com", "netcracker2018");
            String link = BurningLinksManager.getInstance().addNew(userDTO.getEmail());
            String body = String.format(
                    "Hello, %s! Your email address has been used to register on our service.\nTo confirm registration, follow this link: %s",
                    userDTO.getFirstName(),
                    link
            );
            sender.sendMail("Confirm registration", body, "netcracker", userDTO.getEmail());
            return ResponseEntity.ok(new AuthResponse(token));
        } catch (Exception e) {
            return new ResponseEntity<Object>(
                    e.getMessage(),
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private static class AuthForm {
        String username;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        String password;
    }

    private static class AuthResponse{
        public AuthResponse(String token) {
            this.token = token;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        String token;
    }

    @PostMapping("/authorize")
    public ResponseEntity<?> authorize(@RequestBody AuthForm authForm) {
        try {
            String token = securityService.login(authForm.username, authForm.password);
            return ResponseEntity.ok(new AuthResponse(token));
        } catch (Exception e) {
            return new ResponseEntity<Object>(
                    "Wrong email or password",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        try {
            securityService.logout();
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<Object>(
                    e.getMessage(),
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    @RequestMapping(value = "/refresh", method = RequestMethod.GET)
    public ResponseEntity<?> authenticationRequest(HttpServletRequest request) {
        try {
            String token = request.getHeader(TokenUtils.tokenHeader);
            String newToken = securityService.refresh(token);
            return ResponseEntity.ok(new AuthResponse(newToken));
        } catch (Exception e) {
            return new ResponseEntity<Object>(
                    e.getMessage(),
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    /**
     * Удалить пользователя с указанным email
     *
     * @param email - email пользователя
     * @return - HTTP статус OK или BAD REQUEST
     */
    @DeleteMapping("/delete/byEmail/{email:.+}")
    public ResponseEntity<?> delete(@PathVariable String email) {
        try {
            userService.delete(email);
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<Object>(
                    e.getMessage(),
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    /**
     * Удалить пользователя с указанным ID
     *
     * @param id - ID пользователя
     * @return - HTTP статус OK или BAD REQUEST
     */
    @DeleteMapping("/delete/byID/{id}")
    public ResponseEntity<?> delete(@PathVariable long id) {
        try {
            userService.delete(id);
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<Object>(
                    e.getMessage(),
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    /**
     * Сменить имя пользователю
     *
     * @param id   ID пользователя
     * @param name Новое имя
     * @return HTTP статус OK или BAD REQUEST
     */
    @PostMapping("/edit/firstName/{id}/{name}")
    public ResponseEntity<?> editFirstName(@PathVariable long id, @PathVariable String name) {
        try {
            UserDTO userDTO = userService.get(id);
            userDTO.setFirstName(name);
            userService.edit(userDTO);
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<Object>(
                    e.getMessage(),
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    /**
     * Сменить фамилию пользоватею
     *
     * @param id   ID пользователя
     * @param name Новая фамилия
     * @return HTTP статус OK или BAD REQUEST
     */
    @PostMapping("/edit/lastName/{id}/{name}")
    public ResponseEntity<?> editLastName(@PathVariable long id, @PathVariable String name) {
        try {
            UserDTO userDTO = userService.get(id);
            userDTO.setLastName(name);
            userService.edit(userDTO);
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<Object>(
                    e.getMessage(),
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    /**
     * Сменить email пользователю
     *
     * @param id    ID пользователя
     * @param email Новый email
     * @return HTTP статус OK или BAD REQUEST
     */
    @PostMapping("/edit/email/{id}/{email:.+}")
    public ResponseEntity<?> editEmail(@PathVariable long id, @PathVariable String email) {
        try {
            UserDTO userDTO = userService.get(id);
            userDTO.setEmail(email);
            userService.edit(userDTO);
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<Object>(
                    e.getMessage(),
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    /**
     * Сменить пользователю пароль
     *
     * @param id       ID пользователя
     * @param password Новый пароль
     * @return HTTP статус OK или BAD REQUEST
     */
    @PostMapping("/edit/password/{id}/{password}")
    public ResponseEntity<?> editPassword(@PathVariable long id, @PathVariable String password) {
        try {
            UserDTO userDTO = userService.get(id);
            userDTO.setPassword(password);
            userService.edit(userDTO);
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<Object>(
                    e.getMessage(),
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    /**
     * Сменить дату рождения
     *
     * @param id       ID пользователя
     * @param birthday Новая дата рождения
     * @return HTTP статус OK или BAD REQUEST
     */
    @PostMapping("/edit/birthday/{id}/{birthday}")
    public ResponseEntity<?> editBirthday(@PathVariable long id, @PathVariable String birthday) {
        try {
            UserDTO userDTO = userService.get(id);
            userDTO.setBirthday(birthday);
            userService.edit(userDTO);
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<Object>(
                    e.getMessage(),
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    /**
     * Присвоить пользователю новый group ID
     *
     * @param id      ID пользователя
     * @param groupID Новый group ID
     * @return HTTP статус OK или BAD REQUEST
     */
    @PostMapping("/edit/groupID/{id}/{groupID}")
    public ResponseEntity<?> editBirthday(@PathVariable long id, @PathVariable long groupID) {
        try {
            UserDTO userDTO = userService.get(id);
            UserGroup group = groupService.get(groupID);
            userDTO.setGroupID(group);
            userService.edit(userDTO);
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<Object>(
                    e.getMessage(),
                    HttpStatus.BAD_REQUEST
            );
        }
    }
}

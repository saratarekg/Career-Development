package com.example.demo.services;


import com.example.demo.dtos.TitlesDTO;
import com.example.demo.dtos.UserScoresDTO;
import com.example.demo.dtos.UsersDTO;
import com.example.demo.dtos.UsersSignUpDTO;
import com.example.demo.entities.Role;
import com.example.demo.entities.Titles;
import com.example.demo.entities.Users;
import com.example.demo.exceptions.UserAlreadyExistsException;
import com.example.demo.mappers.TitlesMapper;
import com.example.demo.mappers.UsersMapper;
import com.example.demo.repositories.RoleRepository;
import com.example.demo.repositories.TitlesRepository;
import com.example.demo.repositories.UsersRepository;
import com.example.demo.exceptions.InvalidCredentialsException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class UsersServices {

    private final UsersRepository usersRepository;
    private final RoleRepository roleRepository;
    private final TitlesRepository titlesRepository;
    private final TitlesMapper titlesMapper;
    private final UsersMapper usersMapper;
    private final PasswordEncoder passwordEncoder;
    private final TitlesServices titlesServices;


    private final RestTemplate restTemplate;

    @Value("${userScoresService.url}")
    private String userScoresServiceUrl;

    @Autowired
    public UsersServices(UsersRepository usersRepository, TitlesMapper titlesMapper, UsersMapper usersMapper, TitlesRepository titlesRepository, PasswordEncoder passwordEncoder, RoleRepository roleRepository, TitlesServices titlesServices, RestTemplate restTemplate) {
        this.usersRepository = usersRepository;
        this.titlesMapper = titlesMapper;
        this.usersMapper = usersMapper;
        this.titlesRepository = titlesRepository;
        this.passwordEncoder = passwordEncoder;
        this.roleRepository = roleRepository;
        this.titlesServices = titlesServices;
        this.restTemplate = restTemplate;
    }

    public UsersDTO getUser(UUID id) {
        Users user = usersRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User with id " + id + " not found"));

        return usersMapper.toUsersDTO(user);
    }

    public UUID getManager(UUID id) {
        Users user = usersRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User with id " + id + " not found"));

        return user.getManager().getId();
    }

    public UsersDTO getUserByEmail(String email) {
        Users user = usersRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User with email " + email + " not found"));

        return usersMapper.toUsersDTO(user);
    }
    public UUID loadUserIdByEmail(String email) throws UsernameNotFoundException {
        Users user = usersRepository.findByEmail(email) .orElseThrow(() ->
                new UsernameNotFoundException("User not exists by Username or Email"));

        return user.getId();
    }
    public UsersDTO login(String email, String password) {
        Users user = usersRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User with email " + email + " not found"));

        if (!user.getPassword().equals(password)) {
            throw new InvalidCredentialsException("Invalid password");
        }

        return usersMapper.toUsersDTO(user);
    }

    public UsersSignUpDTO signUp(UsersSignUpDTO usersDTO) {
        if (usersRepository.existsByEmail(usersDTO.getEmail())) {
            throw new UserAlreadyExistsException("User with email " + usersDTO.getEmail() + " already exists.");
        }

        Role userRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new EntityNotFoundException("Role not found"));


        usersDTO.setPassword(passwordEncoder.encode(usersDTO.getPassword()));
        Users user = usersMapper.toUsers(usersDTO);
        user.setRoles(Collections.singleton(userRole));
        Users savedUser = usersRepository.save(user);

        addUserScoreInLearnings(savedUser.getId());

        return usersMapper.toUsersSignupDTO(savedUser);
    }



    public UsersDTO addUser(UsersDTO usersDTO) {
        if (usersRepository.existsByEmail(usersDTO.getEmail())) {
            throw new UserAlreadyExistsException("User with email " + usersDTO.getEmail() + " already exists.");
        }

        Map<String, Object> managerAndTitle = getManagerAndTitle(usersDTO);
        Users manager = (Users) managerAndTitle.get("manager");
        Titles title = (Titles) managerAndTitle.get("title");

        Users user = usersMapper.toUsers(usersDTO);
        user.setManager(manager);
        user.setTitleId(title);

        Users savedUser = usersRepository.save(user);

        addUserScoreInLearnings(savedUser.getId());

        return usersMapper.toUsersDTO(savedUser);
    }

    private void addUserScoreInLearnings(UUID userId) {
        UserScoresDTO userScoresDTO = new UserScoresDTO();
        userScoresDTO.setUserId(userId);
        userScoresDTO.setScore(0); // Initial score of 0

        String url = userScoresServiceUrl + "/add";
        restTemplate.postForObject(url, userScoresDTO, UserScoresDTO.class);
    }

    public UsersDTO updateUsers( UsersDTO usersUpdateDTO) {
        Map<String, Object> managerAndTitle = getManagerAndTitle(usersUpdateDTO);
        Users manager = (Users) managerAndTitle.get("manager");
        Titles title = (Titles) managerAndTitle.get("title");
        UUID id = usersUpdateDTO.getId();

        Users user = usersRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + id));

        if(manager != null)
            user.setManager(manager);

        if(title != null)
            user.setTitleId(title);

        usersMapper.updateUsersFromDto(usersUpdateDTO,user);

        usersRepository.save(user);
        return usersMapper.toUsersDTO(user);
    }

    public void deleteUser(UUID id) {
        if (usersRepository.existsById(id)) {
            deleteScoreUserInLearnings(id);
            usersRepository.deleteById(id);
        } else {
            throw new EntityNotFoundException("User with id " + id + " does not exist.");
        }
    }

    private void deleteScoreUserInLearnings(UUID userId) {
        String url = userScoresServiceUrl + "/deleteUserScore/" + userId;
        restTemplate.delete(url);
    }



    public Page<UsersDTO> getAllUsers(Pageable pageable) {
        Page<Users> usersPage = usersRepository.findAll(pageable);

        if (usersPage.isEmpty()) {
            throw new EntityNotFoundException("No users found");
        }

        return usersPage.map(usersMapper::toUsersDTO);

    }

    public Map<String, Object> getManagerAndTitle(UsersDTO usersDTO) {
        Map<String, Object> result = new HashMap<>();

        Users manager = null;
        if (usersDTO.getManagerId() != null) {
            manager = usersRepository.findById(usersDTO.getManagerId())
                    .orElseThrow(() -> new EntityNotFoundException("Manager not found"));
        }
        result.put("manager", manager);

        Titles title = null;
        if (usersDTO.getTitleId() != null) {
            title = titlesRepository.findById(usersDTO.getTitleId())
                    .orElseThrow(() -> new EntityNotFoundException("Title not found"));
        }
        result.put("title", title);

        return result;
    }


    public UsersDTO freezeUserByEmail(String email) {
        Users user = usersRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found with email: " + email));

        user.setFrozen(true);
        usersRepository.save(user);

        return usersMapper.toUsersDTO(user);
    }

    public UsersDTO unfreezeUserByEmail(String email) {
        Users user = usersRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found with email: " + email));

        user.setFrozen(false);
        usersRepository.save(user);

        return usersMapper.toUsersDTO(user);
    }

    public void deleteUserByEmail(String email) {
        Users user = usersRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found with email: " + email));
        usersRepository.delete(user);
    }


    public void resetPassword(String email, String newPassword) {
        Users user = usersRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found with email: " + email));
        user.setPassword(passwordEncoder.encode(newPassword));
        usersRepository.save(user);
    }


    public void assignManager(String userEmail, String managerEmail) {
        Users user = usersRepository.findByEmail(userEmail)
                .orElseThrow(() -> new EntityNotFoundException("User not found with email: " + userEmail));

        Users manager = usersRepository.findByEmail(managerEmail)
                .orElseThrow(() -> new EntityNotFoundException("Manager not found with email: " + managerEmail));

        user.setManager(manager);

        usersRepository.save(user);
    }


    public void assignTitleByEmail(String email, UUID titleId) {
        Users user = usersRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found with email: " + email));

        TitlesDTO titleDTO = titlesServices.getTitles(titleId);
        Titles title = titlesMapper.toTitle(titleDTO);
        title.setId(titleId);
        user.setTitleId(title);
        usersRepository.save(user);
    }


    public void assignRoleByEmail(String userEmail, Long roleId) {
        Users user = usersRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("Role not found"));

        user.getRoles().add(role);

        usersRepository.save(user);
    }


    public List<UsersDTO> getManagedUsers(UUID managerId) {
        List<Users> managedUsers = usersRepository.findByManagerId(managerId);
        return managedUsers.stream()
                .map(usersMapper::toUsersDTO)
                .collect(Collectors.toList());    }
}


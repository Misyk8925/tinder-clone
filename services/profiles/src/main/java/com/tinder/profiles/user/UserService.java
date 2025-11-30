package com.tinder.profiles.user;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

@Service
public class UserService {
    private List<NewUserRecord> users;


    @Qualifier("keycloakWebClient")
    private WebClient keycloakWebClient;

    @Autowired
    public UserService(@Qualifier("keycloakWebClient") WebClient keycloakWebClient) {
        this.keycloakWebClient = keycloakWebClient;
    }

    private void setup() {
        this.users = new ArrayList<>();

        String[] firstNames = {"Alexander", "Maria", "Dmitry", "Anna", "Ivan", "Catherine", "Sergey", "Olga", "Andrew", "Natalie", "Michael", "Elena", "Alex", "Tatiana", "Vladimir", "Irina", "Nicholas", "Svetlana", "Paul", "Julia"};
        String[] lastNames = {"Ivanov", "Petrova", "Sidorov", "Kozlova", "Smirnov", "Novikova", "Popov", "Morozova", "Vasiliev", "Volkova", "Sokolov", "Zaitseva", "Lebedev", "Semenova", "Egorov", "Pavlova", "Kozlov", "Golubeva", "Stepanov", "Vinogradova"};

        for (int i = 0; i < 20; i++) {
            String username = "user" + (i + 1) + "@test.com";
            String password = "Password" + (i + 1) + "!";
            String firstName = firstNames[i];
            String lastName = lastNames[i];

            users.add(new NewUserRecord(username, password, firstName, lastName));
        }

        // Вывод для проверки
        System.out.println("Создано тестовых пользователей: " + users.size());
        users.forEach(user -> System.out.println("Username: " + user.username() + ", Name: " + user.firstName() + " " + user.lastName()));
    }



    public void createTestUsers(){
        setup();
        users.forEach(user -> {
            keycloakWebClient.post()
                    .uri("/auth/users")
                    .bodyValue(user)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        });
    }

    public List<NewUserRecord> getUsers() {
        return users;
    }

}

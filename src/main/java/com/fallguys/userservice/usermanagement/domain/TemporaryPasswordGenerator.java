package com.fallguys.userservice.usermanagement.domain;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class TemporaryPasswordGenerator {

    private static final int PASSWORD_LENGTH = 12;
    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS = "0123456789";
    private static final String ALL = LETTERS + DIGITS;
    private static final SecureRandom RANDOM = new SecureRandom();

    private TemporaryPasswordGenerator() {
    }

    static String generate() {
        List<Character> characters = new ArrayList<>();
        characters.add(randomCharacter(LETTERS));
        characters.add(randomCharacter(DIGITS));

        while (characters.size() < PASSWORD_LENGTH) {
            characters.add(randomCharacter(ALL));
        }

        Collections.shuffle(characters, RANDOM);

        StringBuilder password = new StringBuilder(PASSWORD_LENGTH);
        for (Character character : characters) {
            password.append(character);
        }

        return password.toString();
    }

    private static char randomCharacter(String source) {
        return source.charAt(RANDOM.nextInt(source.length()));
    }
}

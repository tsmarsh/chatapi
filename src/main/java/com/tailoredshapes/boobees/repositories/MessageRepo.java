package com.tailoredshapes.boobees.repositories;

import com.tailoredshapes.boobees.model.Prompt;

import java.util.List;

public interface MessageRepo {
    List<Prompt> findN(Long chatId, int n, String prompt);

    void createAll(Long chatId, List<Prompt> chatPrompts);
}

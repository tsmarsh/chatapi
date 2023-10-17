package com.tailoredshapes.boobees.repositories;

import com.tailoredshapes.boobees.model.Prompt;

import java.util.List;

public interface MessageRepo {
    List<Prompt> findLastN(Long chatId, int n);

    void createAll(Long chatId, List<Prompt> chatPrompts);
}

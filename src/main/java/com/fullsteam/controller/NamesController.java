package com.fullsteam.controller;

import com.fullsteam.RandomNames;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

import java.util.List;

/**
 * Serves the curated player-name list so the frontend can populate the
 * name-picker dropdown in the loadout modal.
 */
@Controller("/api")
public class NamesController {

    @Get("/names")
    public List<String> getNames() {
        return RandomNames.getNames();
    }
}

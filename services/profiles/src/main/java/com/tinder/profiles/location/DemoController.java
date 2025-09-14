package com.tinder.profiles.location;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/demo")
public class DemoController {

    private final LocationService locationService;
    public DemoController(LocationService locationService) {
        this.locationService = locationService;
    }

    @PostMapping()
    public ResponseEntity<Location> demo(@RequestBody LocationDto locationDto) {

        Location nLoc = locationService.create("demo", locationDto.getGeo().getX(), locationDto.getGeo().getY());
        return new ResponseEntity<>(nLoc, HttpStatus.CREATED);
    }
}


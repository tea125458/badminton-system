package com.badminton.pickupgame;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
public class DataGeneratorController {

    @Autowired
    private DataGeneratorService dataGeneratorService;

    @PostMapping("/generate-million-games")
    public ResponseEntity<String> generateData() {
        try {
            String result = dataGeneratorService.generateMillionPickupGames();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("產生失敗：" + e.getMessage());
        }
    }

    @GetMapping("/measure-query")
    public ResponseEntity<String> measureQuery() {
        try {
            return ResponseEntity.ok(dataGeneratorService.measureSlowQuery());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("查詢失敗：" + e.getMessage());
        }
    }

    @PostMapping("/create-index")
    public ResponseEntity<String> createIndex() {
        return ResponseEntity.ok(dataGeneratorService.createCompositeIndex());
    }

    @PostMapping("/drop-index")
    public ResponseEntity<String> dropIndex() {
        return ResponseEntity.ok(dataGeneratorService.dropCompositeIndex());
    }
}

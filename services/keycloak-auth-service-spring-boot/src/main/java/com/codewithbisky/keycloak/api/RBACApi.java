package com.codewithbisky.keycloak.api;


import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/rbac")
public class RBACApi {





    @GetMapping("merge-role")
    @PreAuthorize("hasAnyRole('DEV')")
    public ResponseEntity<?> testForMergeRole() {


        System.out.println("SOMETHING ");
        return ResponseEntity.status(HttpStatus.OK).body("I HAVE ACCESS to MERGE ROLE");
    }


}

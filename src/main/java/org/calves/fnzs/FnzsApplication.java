package org.calves.fnzs;

import org.calves.fnzs.controller.FnzsController;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FnzsApplication {

    public static void main(String[] args) {
        FnzsController.loadUsernames();
        SpringApplication.run(FnzsApplication.class, args);
    }

}

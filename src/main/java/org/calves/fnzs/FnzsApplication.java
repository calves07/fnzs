package org.calves.fnzs;

import dto.misc.Configuration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FnzsApplication {

    public static void main(String[] args) {
        SpringApplication.run(FnzsApplication.class, args);
        System.out.println(Configuration.getInstance().getMongo().getConnectionString());
    }

}

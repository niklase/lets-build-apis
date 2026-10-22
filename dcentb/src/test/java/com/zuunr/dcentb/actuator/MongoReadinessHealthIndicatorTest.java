package com.zuunr.dcentb.actuator;

import com.zuunr.mongodb.MongoJsonDB;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MongoReadinessHealthIndicatorTest {

    @Test
    void upWithNoDetailsWhenPingSucceeds() {
        MongoJsonDB mongoJsonDB = Mockito.mock(MongoJsonDB.class);
        MongoReadinessHealthIndicator indicator = new MongoReadinessHealthIndicator(mongoJsonDB);

        Health health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertTrue(health.getDetails().isEmpty());
    }

    @Test
    void downWithOnlyAFixedReasonNeverTheRawExceptionWhenPingThrows() {
        MongoJsonDB mongoJsonDB = Mockito.mock(MongoJsonDB.class);
        Mockito.doThrow(new RuntimeException("connection to mongodb://admin:S3cr3t@10.0.0.5:27017 refused"))
                .when(mongoJsonDB).ping();
        MongoReadinessHealthIndicator indicator = new MongoReadinessHealthIndicator(mongoJsonDB);

        Health health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals(1, health.getDetails().size());
        assertEquals("mongo unreachable", health.getDetails().get("reason"));
    }
}

package com.practice.spring_ai.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Controller;

import java.time.LocalTime;
import java.time.ZoneId;

@Controller
@Slf4j
public class TimeTools {

    @Tool(name = "getCurrentLocalTime", description = "Get the current local date and time")
    public String getCurrentLocalTime() {
        log.info("Getting current local time");
        return LocalTime.now().toString();
    }

    @Tool(name = "getCurrentDateTime", description = "Get the current date for given time zone")
    public String getCurrentDateTime(@ToolParam(description = "Value representing time zone ID, example Africa/Cairo") String timeZone) {
        log.info("Getting current date for time zone: {}", timeZone);
        return LocalTime.now(ZoneId.of(timeZone)).toString();
    }
}

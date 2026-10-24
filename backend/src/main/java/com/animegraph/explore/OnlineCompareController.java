package com.animegraph.explore;
import com.animegraph.common.ApiResponse;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/explore/compare")
public class OnlineCompareController {
    private final OnlineCompareService service;
    public OnlineCompareController(OnlineCompareService service){this.service=service;}
    @GetMapping public ApiResponse<?> compare(@RequestParam long subjectA,@RequestParam long subjectB){return ApiResponse.ok(service.compare(subjectA,subjectB));}
}

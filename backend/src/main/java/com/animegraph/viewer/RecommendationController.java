package com.animegraph.viewer;
import com.animegraph.common.*;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1")
public class RecommendationController {
    private final ViewerService viewers;private final ViewerIdentity identity;private final ViewerAnalysisMapper mapper;private final CandidateService candidates;
    public RecommendationController(ViewerService viewers,ViewerIdentity identity,ViewerAnalysisMapper mapper,CandidateService candidates){this.viewers=viewers;this.identity=identity;this.mapper=mapper;this.candidates=candidates;}
    @GetMapping("/viewer/recommendations")public ApiResponse<?> read(HttpServletRequest request,HttpServletResponse response,@RequestParam(defaultValue="0")int offset,@RequestParam(defaultValue="HYBRID")String algorithm){
        response.setHeader("Cache-Control","no-store");if(offset<0||offset>50||!Set.of("POPULAR","CONTENT","HYBRID").contains(algorithm))throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","推荐参数不正确");String id=identity.read(request);var state=viewers.snapshot(id);var result=RecommendationEngine.rank(state.collections(),candidates.all(),new HashSet<>(mapper.excluded(id)),offset,algorithm);var current=viewers.binding(id);if(current==null||!current.revision().equals(state.binding().revision())||!Objects.equals(current.lastSyncedAt(),state.binding().lastSyncedAt()))throw new ApiException(HttpStatus.CONFLICT,"VIEWER_CHANGED","账号或片单已变化，请刷新");return ApiResponse.ok(Map.of("result",result,"candidateState",candidates.state(),"excluded",mapper.excluded(id)));
    }
    public record Feedback(@Min(1)@Max(Integer.MAX_VALUE)long subjectId,@Pattern(regexp="NOT_INTERESTED|RESTORE")@NotNull String action){}
    @PostMapping("/viewer/recommendation-feedback")public ApiResponse<?> feedback(@Valid @RequestBody Feedback body,HttpServletRequest request,HttpServletResponse response){response.setHeader("Cache-Control","no-store");String id=identity.read(request);var state=viewers.snapshot(id);if(body.action().equals("RESTORE"))mapper.restore(id,state.binding().revision(),body.subjectId());else mapper.exclude(id,state.binding().revision(),body.subjectId());return ApiResponse.ok(null);}
    @PostMapping("/viewer/recommendation-jobs")@ResponseStatus(HttpStatus.ACCEPTED)public ApiResponse<?> start(HttpServletRequest request){viewers.snapshot(identity.read(request));return ApiResponse.ok(candidates.start());}
    @GetMapping("/community/overview")public ApiResponse<?> community(){return ApiResponse.ok(candidates.community());}
}

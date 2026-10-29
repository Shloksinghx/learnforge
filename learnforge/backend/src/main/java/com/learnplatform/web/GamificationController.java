package com.learnplatform.web;

import com.learnplatform.config.AuthUser;
import com.learnplatform.config.CurrentUser;
import com.learnplatform.service.GamificationService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GamificationController {
    private final GamificationService gamification;

    public GamificationController(GamificationService gamification) {
        this.gamification = gamification;
    }

    @GetMapping("/api/me/gamification")
    public GamificationService.Profile profile() {
        return gamification.profile(CurrentUser.get().userId());
    }

    @GetMapping("/api/leaderboard")
    public List<GamificationService.LeaderRow> leaderboard() {
        AuthUser me = CurrentUser.get();
        return gamification.leaderboard(me.orgId(), me.userId(), 20);
    }
}

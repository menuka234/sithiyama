package lk.sithiyama.backend.repository;

import java.util.UUID;
import lk.sithiyama.backend.domain.Route;
import lk.sithiyama.backend.domain.RouteLike;
import lk.sithiyama.backend.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteLikeRepository extends JpaRepository<RouteLike, UUID> {
    boolean existsByUserAndRoute(User user, Route route);
    long countByRoute(Route route);
    void deleteByUserAndRoute(User user, Route route);
}
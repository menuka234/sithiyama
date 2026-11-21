package lk.sithiyama.backend.repository;

import java.util.Optional;
import java.util.UUID;
import lk.sithiyama.backend.domain.Route;
import lk.sithiyama.backend.domain.RouteFavourite;
import lk.sithiyama.backend.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteFavouriteRepository extends JpaRepository<RouteFavourite, UUID> {
    Page<RouteFavourite> findByUser(User user, Pageable pageable);
    Optional<RouteFavourite> findByUserAndRoute(User user, Route route);
    boolean existsByUserAndRoute(User user, Route route);
    void deleteByUserAndRoute(User user, Route route);
}
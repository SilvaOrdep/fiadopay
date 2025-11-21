package edu.ucsal.fiadopay.domain.repository;
import edu.ucsal.fiadopay.domain.model.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface MerchantRepository extends JpaRepository<Merchant, Long> {
  Optional<Merchant> findByClientId(String clientId);
  boolean existsByName(String name);
}

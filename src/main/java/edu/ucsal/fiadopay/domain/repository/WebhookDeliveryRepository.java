package edu.ucsal.fiadopay.domain.repository;
import edu.ucsal.fiadopay.domain.model.WebhookDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, Long> { }

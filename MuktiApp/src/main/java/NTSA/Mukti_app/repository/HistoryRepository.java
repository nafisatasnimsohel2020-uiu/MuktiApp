package NTSA.Mukti_app.repository;

import NTSA.Mukti_app.model.History;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface HistoryRepository extends JpaRepository<History, Long> {
    // শুধুমাত্র নির্দিষ্ট ইউজারের ফোন নম্বর অনুযায়ী হিস্টোরি খোঁজা
    List<History> findByUserPhoneOrderByActivityTimeDesc(String userPhone);

    // Food Post ID অনুযায়ী হিস্টোরি খোঁজা
    List<History> findByFoodPostId(Long foodPostId);

    List<History> findByFoodPostIdAndIsRequestDonation(Long foodPostId, Boolean isRequestDonation);
}

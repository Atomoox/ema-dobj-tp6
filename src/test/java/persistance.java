import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.ema.imanip.Club;
import org.ema.imanip.EntityManagerImpl;

import java.lang.reflect.Field;
import java.sql.SQLException;

public class persistance {
    @Test
    public void testPersistence() {
        Club club = new Club();
        club.setFabricant("un nom");
        club.setPoids(10.3);

        EntityManagerImpl em = new EntityManagerImpl();
        em.persist(club);
    }


    @Test
    public void testFindAndMerge() throws SQLException {
        Club club = new Club();
        club.setFabricant("un nom");
        club.setPoids(10.3);

        EntityManagerImpl em = new EntityManagerImpl();
        Club trouve1 = em.<Club>find(Club.class, club.getId());

        trouve1.printInfos();

        club.setFabricant("new");
        club.setPoids(9.7);

        em.merge(club);

        Club trouve2 = em.<Club>find(Club.class, club.getId());

        trouve2.printInfos();

        assertEquals("un nom", trouve1.getFabricant());
        assertEquals(club.getFabricant(), trouve2.getFabricant());
    }
}

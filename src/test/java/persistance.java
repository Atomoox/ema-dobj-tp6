import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.ema.imanip.Club;
import org.ema.imanip.EntityManagerImpl;

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
    public void testFind() throws SQLException {
        Club club = new Club();
        club.setFabricant("un  nom");
        club.setPoids(10.3);

        EntityManagerImpl em = new EntityManagerImpl();
        Club trouve = em.<Club>find(Club.class, club.getId());
        assertEquals(club.getFabricant(), trouve.getFabricant());
    }

}

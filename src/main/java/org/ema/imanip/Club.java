package org.ema.imanip;

public class Club {
    private long id;

    private int version = 0;

    private String fabricant;

    private double poids;


    public double getPoids() {
        return poids;
    }

    public void setPoids(double poids) {
        this.poids = poids;
    }

    public long getId() {
        return id;
    }

    public String getFabricant() {
        return fabricant;
    }

    public void setFabricant(String fabricant) {
        this.fabricant = fabricant;
    }

    public void printInfos() {
        System.out.println(
                String.format(
                        "Version: %d, id: %d, poids: %f, fabricant: %s",
                        this.version,
                        this.id,
                        this.poids,
                        this.fabricant
                )
        );
    }
}

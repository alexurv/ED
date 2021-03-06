package EstructuraDeDades;

import EstructuraDeDades.Part1.DoublyLinkedList;
import Exceptions.*;
import com.google.gson.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ICAENGraph {
    public Graph<Estacio, Double> graf;
    private List<Estacio> estacions;

    public ICAENGraph(String jsonName){
        try {
            estacions = readFile(jsonName);
            graf = new Graph<>(estacions);
            addEdges();
        }
        catch (IOException e){
            e.printStackTrace();
        }
    }

    /**
     * camiOptim: funció que busca els punst de càrrega pels quals ha de passar
     *              per arribar al seu destí.
     *            L'algorisme implementat és l'A*
     *
     * @param start String que representa el nom de l'estació inicial
     * @param target String que representa el nom de l'estació destí
     * @param range int que representa l'autonomia del cotxe
     * @return una llista que conté tots els noms dels punts de càrrega pels quals
     *          ha de passar per arribar al seu destí
     * @throws UnreachablePath en cas que no es pugui crear la llista amb el resultat
     */
    public List<String> optimalPath(String start, String target, int range) throws UnreachablePath {
        // the set of discovered nodes that may need to be (re-)expanded
        // initially, only the start node is known
        List<String> openSet = new ArrayList<>();
        openSet.add(start);

        // get their Estacio object to use their methods
        Estacio init = getEstacioByString(start);
        Estacio end = getEstacioByString(target);

        assert init != null;
        assert end != null;

        // for node n, cameFrom[n] is the node immediately preceding it on the cheapest path from start
        // to n currently known
        HashMap<String, String> cameFrom = new HashMap<>();

        HashMap<String, Double> gScore = new HashMap<>();
        HashMap<String, Double> fScore = new HashMap<>();
        for (Estacio est : estacions){
            gScore.put(est.getNom(), Double.MAX_VALUE);
            fScore.put(est.getNom(), Double.MAX_VALUE);
        }

        // for node n, gScore[n] is the cost of the cheapest path from start to n currently known
        gScore.put(start, 0.0);

        // for node n, fScore[n] := gScore[n] + h(n). fScore[n] represents our current best guess as to
        // how cheap a path could be from start to finish if it goes through n
        fScore.put(start, distance(init.getLatitud(), init.getLongitud(), end.getLatitud(), end.getLongitud()));

        while (!openSet.isEmpty()){
            Estacio current = lowestScore(openSet, fScore);

            if (current.equals(end)) {
                return reconstruct_path(cameFrom, current.getNom());
            }

            openSet.remove(current.getNom());
            try {
                for (Estacio neighbor : graf.adjacent(current)) {
                    // tentative_gScore is the distance from start to the neighbor through current
                    double distance = 0.0;
                    try {
                        distance = graf.edgeValue(current, neighbor);
                    }
                    catch (NonExistentEdge e){
                        e.printStackTrace();
                    }
                    double tentative_gScore = gScore.get(current.getNom()) + distance;

                    if ((tentative_gScore < gScore.get(neighbor.getNom())) && (range - distance >= 0)) {
                        // this path to neighbor is better than any previous one
                        cameFrom.put(neighbor.getNom(), current.getNom());
                        gScore.put(neighbor.getNom(), tentative_gScore);
                        fScore.put(neighbor.getNom(), tentative_gScore + distance(neighbor.getLatitud(), neighbor.getLongitud(),
                                end.getLatitud(), end.getLongitud()));
                        if (!openSet.contains(neighbor.getNom())) {
                            openSet.add(neighbor.getNom());
                        }
                    }
                }
            }
            catch (NotFound e){
                e.printStackTrace();
            }
        }
        // open set is empty but goal was never reached
        throw new UnreachablePath();
    }

    /**
     * zonesDistMaxNoGarantida: funció que retorna aquelles zones de recàrrega que no
     *                          compleixen la condició d'estar enllaçades amb, almenys,
     *                          una altra zona de recàrrega a una distància màxima
     *                          determinada per l'autonomia
     *
     * @param id_origen String que representa l'estació origen
     * @param range int que representa l'autonomia del cotxe
     * @return una llista que conté aquelles zones de recàrrega que no estan enllaçades
     *          amb una altra zona de recàrrega a una distància màxima
     */
    public List<String> notGuaranteedMaxDistanceZones(String id_origen, int range){
        List<String> notGuaranteed = new ArrayList<>();

        // check for all stations if there is an optimal path, if there isn't
        // optimalPath will throw an exception, in that case add that station to the list
        // of not guaranteed maximum distance zones
        for (Estacio est : estacions){
            try{
                if (!est.getNom().equalsIgnoreCase(id_origen))
                    optimalPath(id_origen, est.getNom(), range);
            }
            catch (UnreachablePath e){
                notGuaranteed.add(est.getNom());
            }
        }
        return notGuaranteed;
    }

    /*------------------------------------------------------------------------------*/
    /*                                                                              */
    /*                              Auxiliary methods                               */
    /*                                                                              */
    /*------------------------------------------------------------------------------*/

    private List<Estacio> readFile(String pathname) throws IOException{
        List<Estacio> estacions = new ArrayList<>();

        InputStream is = new FileInputStream(pathname);
        Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
        JsonStreamParser parser = new JsonStreamParser(reader);

        JsonElement elem = parser.next();
        JsonArray jarr = elem.getAsJsonArray();

        ArrayList<String> list = new ArrayList<>();

        // add to 'list' every object removing unnecessary characters
        for (int i = 0; i < jarr.size(); i++) {
            String str = jarr.get(i).toString();
            str = str.replaceAll("\"", "");
            str = str.replaceAll("\\{", "");
            str = str.replaceAll("}", "");
            list.add(str);
        }

        for (String val : list){
            String[] arr = val.split(",");
            List<String> estFields = new ArrayList<>();

            boolean commas = arr.length > 13;

            for (int i = 0; i < arr.length; i++){
                String[] value = arr[i].split(":");

                if((arr[i].contains("nom")) && (!arr[i+1].contains("data"))){
                    String c = value[1] + ", ";
                    for (int j = 1; j < arr.length % 13; j++) {
                        c += arr[i + j] + " ";
                    }
                    c += arr[i + arr.length % 13];
                    estFields.add(c);
                    i += (arr.length % 13);
                }
                else{
                    if(arr[i].contains("data")){
                        String data = value[1]+":"+value[2]+":"+value[3];
                        estFields.add(data);
                    }
                    else{
                        if((arr[i].contains("carrer")) && ((!arr[i+1].contains("ciutat")))){
                            String c = value[1] + ", ";
                            for (int j = 1; j < arr.length % 13; j++) {
                                c += arr[i + j] + " ";
                            }
                            c += arr[i + arr.length % 13];
                            estFields.add(c);
                            i += (arr.length % 13);
                        }
                        else{
                            if (value.length == 1) {
                                estFields.add("");
                            }
                            else {
                                estFields.add(value[1]);
                            }
                        }
                    }
                }
            }

            double consum = switch (estFields.get(4)) {
                case "", "0.0" -> 0;
                default -> Double.parseDouble(estFields.get(4));
            };

            double potencia = estFields.get(9).equals("") ? 0.0 : Double.parseDouble(estFields.get(9));

            int temps = estFields.get(8).equals("") ? 0 : Integer.parseInt(estFields.get(8));

            DoublyLinkedList<Endoll> endolls = new DoublyLinkedList<>();
            Endoll endoll = new Endoll(Integer.parseInt(estFields.get(0)),
                    consum, temps,
                    estFields.get(7), estFields.get(10));
            endolls.insert(endoll);

            Estacio estacio = new Estacio(Integer.parseInt(estFields.get(1)),
                    potencia, estFields.get(2), estFields.get(3),
                    estFields.get(5), estFields.get(6), Double.parseDouble(estFields.get(11)),
                    Double.parseDouble(estFields.get(12)), endolls);

            estacions.add(estacio);
        }

        List<Estacio> result = new ArrayList<>();
        for (int i = 0; i < estacions.size(); i++){
            Estacio estacio = estacions.get(i);
            result.add(estacio);
            estacions.remove(estacio);
            for (int j = 0; j < estacions.size(); j++){
                Estacio temp = estacions.get(j);
                if ((temp.getLatitud() == estacio.getLatitud()) && (temp.getLongitud() == estacio.getLongitud())){
                    try {
                        result.get(i).getEndolls().insert(temp.getEndolls().get(0));
                        estacions.remove(temp);
                        j--;
                    }
                    catch (NotFound e){
                        e.printStackTrace();
                    }
                }
            }
            if (estacions.isEmpty())
                break;
        }
        return result;
    }
    private void addEdges(){
        // adding all edges between stations withing 40 km range
        for (Estacio est : estacions){
            for (Estacio stat : estacions){
                double dist = distance(est.getLatitud(), est.getLongitud(), stat.getLatitud(), stat.getLongitud());
                if ((dist < 40.0) && !est.equals(stat)){
                    try {
                        graf.addEdge(est, stat, dist);
                    }
                    catch (CannotAddElement ignored){}
                }
            }
        }

        // adding an edge to all stations which don't have any connections
        for (Estacio est : estacions){
            try {
                if (graf.adjacent(est).isEmpty()) {
                    Estacio minDistEst = estacions.get(0);
                    double minDist = distance(minDistEst.getLatitud(), minDistEst.getLongitud(), est.getLatitud(), est.getLongitud());
                    for (Estacio stat : estacions){
                        double dist2 = distance(stat.getLatitud(), stat.getLongitud(), est.getLatitud(), est.getLongitud());
                        if((dist2 < minDist) && !est.equals(stat)){
                            minDist = dist2;
                            minDistEst = stat;
                        }
                    }
                    graf.addEdge(est, minDistEst, minDist);
                }
            }
            catch (NotFound | CannotAddElement ignored){}
        }
    }

    private Estacio getEstacioByString(String id){
        for (Estacio est : estacions){
            if(est.getNom().equals(id))
                return est;
        }
        return null;
    }

    private double distance(double lat1, double lon1, double lat2, double lon2){
        // The math module contains a function
        // named toRadians which converts from
        // degrees to radians
        lon1 = Math.toRadians(lon1);
        lon2 = Math.toRadians(lon2);
        lat1 = Math.toRadians(lat1);
        lat2 = Math.toRadians(lat2);

        // Haversine formula
        double dlon = lon2 - lon1;
        double dlat = lat2 - lat1;
        double a = Math.pow(Math.sin(dlat / 2), 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.pow(Math.sin(dlon / 2),2);

        double c = 2 * Math.asin(Math.sqrt(a));

        // Radius of earth in kilometers
        double r = 6371;

        // calculate the result
        return(c * r);
    }

    private Estacio lowestScore(List<String> list, HashMap<String, Double> map){
        Estacio res = null;
        double min = Double.MAX_VALUE;

        for (String str : list){
            if (min > map.get(str)) {
                min = map.get(str);
                res = getEstacioByString(str);
            }
        }
        return res;
    }

    private List<String> reconstruct_path(HashMap<String, String> cameFrom, String current){
        List<String> total_path = new ArrayList<>();

        String key = current;
        total_path.add(key);

        // traces back all the keys and values related to current from the map
        while (key != null){
            key = cameFrom.get(key);
            if (key != null) total_path.add(key);
        }

        Collections.reverse(total_path);
        return total_path;
    }

    public void edgeValues(){
        estacions.forEach( x -> {
                try {
                    System.out.println(x.getNom() + " adjacents: ");
                    List<Estacio> adj = graf.adjacent(x);
                    adj.forEach( a -> {
                        try {
                            System.out.println(a.getNom() + " dist: " + graf.edgeValue(x, a));
                        } catch (NonExistentEdge e) {
                            e.printStackTrace();
                        }
                    });
                } catch (NotFound e) {
                    e.printStackTrace();
                }
            });
    }
}

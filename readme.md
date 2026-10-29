Rendszer és Időjárásfigyelő (JavaFX System & Weather Monitor).
Valós idejű asztali alkalmazás, amely részletes információkat nyújt a számítógép hardveres állapotáról, a hálózati forgalomról, az aktuális időjárásról, valamint naptári adatokról.
Mire való? 
Az alkalmazás egy központi, könnyen áttekinthető műszerfalat (Dashboard) biztosít, ahol folyamatosan nyomon követhető a processzor, a memória, a videókártya és az adattárolók terhelése, 
valamint a hálózati sebesség és az aktuális időjárás.
Debian alapú Linux-ra készült.
Előfeltételek és Telepítendő Programok:
1. Alapvető fejlesztői környezet:
Java JDK 21 vagy újabb (pl. OpenJDK 21, Temurin 21).
Apache Maven 3.8+ (a függőségek kezeléséhez és a fordításhoz).
Esetleg, ha még nincsen, akkor:

sudo apt update
sudo apt install -y openjdk-21-jdk maven
java -version
mvn -version

2. Linux-specifikus függőségek (a hardveres szenzorok és GPU detektálásához).
Linux rendszereken a mélyebb hardveres adatok (GPU típus, hőmérsékletek, kártyaadatok) eléréséhez érdemes az alábbi csomagokat telepíteni:

sudo apt update
sudo apt install pciutils lm-sensors vulkan-tools mesa-utils

Az Alkalmazás Indítása:
A projekt Maven segítségével könnyen fordítható és indítható.
1. Projekt fordítása:
Irány a projekt gyökérkönyvtára a terminálban, majd futtasd:

mvn clean package

2. Indítás Maven segítségével.
A javafx-maven-plugin integrációnak köszönhetően az alkalmazás egyetlen paranccsal indítható:

mvn javafx:run

Főbb Funkciók
Dátum és Idő: 
Pontos idő, dátum, az év hete, az év napja és időszámításunk óta eltelt napok száma.
Időjárás: 
Valós idejű Open-Meteo API integráció (hőmérséklet, hőérzet, páratartalom, szélsebesség, állapot).
Operációs Rendszer: 
OS verzió, Kernel, architektúra, asztali környezet, ablakrendszer és futási idő (Uptime).
Alaplap & Szenzorok: 
Alaplap modell, BIOS verzió és kiadási dátum, hőmérséklet- és ventilátorszenzorok élő adatai.
Processzor (CPU): 
Modellnév, magok és szálak száma, valós idejű terhelés (%), átlagos órajel és hőmérséklet.
Memória: 
RAM és SWAP felhasznált és teljes kapacitása, százalékos terheléssel.
Videokártya (GPU): 
Fejlett PCI-, Vulkan- és OpenGL-alapú GPU felismerés, VRAM használat, órajelek, hőmérsékletek és ventilátorsebességek.
Hálózat & Geolokáció: 
Nyilvános IP-cím, származási ország és város megjelenítése, valamint bejövő/kimenő pillanatnyi sebesség és mért adatforgalom.
Adattárolók: 
Csatolt partíciók, fájlrendszer típusok, használt és szabad tárhelyek vizuális listája.


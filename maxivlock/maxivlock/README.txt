MAX IV LOCK - mod de Fabric para Cobbreeding 2.4.0 (Minecraft 1.21.1)

QUE HACE
Si los dos padres tienen 31 en una estadistica, el huevo sale con 31 en esa
estadistica. Por defecto aplica a las 6. Para dejarlo solo en Ataque, edita
LOCKED en BreedingUtilitiesMixin.java y deja solo Stats.ATTACK.

COMO COMPILARLO (necesitas Java 21 y Gradle)
1. Copia el jar de Cobblemon de tu carpeta mods/ a libs/cobblemon-fabric.jar
2. Dentro de esta carpeta ejecuta:   gradle build
   (si tienes el wrapper de otro proyecto de Fabric, usa ./gradlew build)
3. El mod queda en build/libs/maxivlock-1.0.0.jar
4. Copialo a la carpeta mods/ del servidor y reinicia.

COMO COMPROBARLO
- En la consola, al sacar un huevo, deberia salir:
  "Huevo: attack fijado a 31 (ambos padres tenian 31)"
- Si al arrancar el servidor da un error de mixin sobre calcStats, Cobbreeding
  no es la version 2.4.0 o cambio ese metodo.

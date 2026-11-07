# Creature Studio — NeoForge 1.21.1

Crea **tus propias criaturas dentro de Minecraft**, sin programar: modélalas con cubos (como en
Blockbench), elige texturas y colores, anima sus partes y define cómo se comportan (como en MCreator).

## Características

**Modelado (pestaña «Modelo»)**
- Vista 3D en tiempo real: arrastra para girar, clic derecho para mover la cámara, rueda para el zoom.
- Haz clic sobre una pieza en la vista 3D para seleccionarla (se resalta en amarillo, con su pivote en rojo).
- Hasta 128 cubos por criatura, con **jerarquía padre/hijo** (los ojos siguen a la cabeza, etc.).
- Posición, tamaño, pivote y rotación editables a mano o con la rueda del ratón sobre el campo.
- **Espejar X**: crea al instante la pata, el ojo o el ala del otro lado.
- **9 plantillas**: vacía, cuadrúpedo, bípedo, araña, pájaro, slime, serpiente, gólem y dragón.
- Más de 60 texturas: 15 propias (pelaje, escamas, plumas, manchas, ojos, bocas, colmillos…) y
  cualquier textura de bloque de Minecraft (lana, piedra, oro, lava animada…), todas teñibles con
  un selector de color HSV.
- Piezas que **brillan en la oscuridad** (ojos de araña, núcleos mágicos…).
- **15 animaciones**: cabeza que mira, piernas, brazos que golpean, patas laterales de araña, alas que
  aletean, colas, rebote, mandíbula, giro y balanceo. Botones para probar «Caminar» y «Atacar».
- Deshacer/rehacer (Ctrl+Z / Ctrl+Y), guardar con Ctrl+S, mover piezas con las flechas.

**Comportamiento (pestaña «Comportamiento»)**
- Vida, daño, velocidad, armadura, resistencia al empuje, rango de visión, escala y experiencia.
- Actitud: pasiva (huye), neutral (se defiende) u hostil (persigue jugadores).
- Camina o **vuela**.
- Ataque cuerpo a cuerpo o a distancia: flechas, bolas de nieve, bolas de fuego o escupitajo.
- Efecto al golpear: veneno, wither, lentitud, debilidad, fuego, levitación, ceguera o náuseas.
- 27 conjuntos de sonidos de mobs vanilla (con botón para escucharlos).
- Inmune al fuego, arde con el sol, se deja atraer con un ítem.
- Botín al morir (ítem y cantidad).
- **Aparición natural** opcional: de día, de noche o siempre, filtrando por biomas o etiquetas
  (`minecraft:plains, #minecraft:is_forest`), con probabilidad y máximo de criaturas cercanas.
- Colores del huevo personalizables.

**Además**
- Huevos de criatura con el nombre y los colores de cada criatura.
- Exportar/importar criaturas como `.json` para compartirlas con amigos.
- Funciona en un jugador y en servidores (el mod debe estar instalado en el servidor y en los clientes).

## Cómo compilarlo con GitHub (sin instalar nada)

1. Crea un repositorio nuevo en GitHub (puede ser privado).
2. Descomprime el `.zip` y entra en la carpeta `CreatureStudio`.
3. En la página del repositorio pulsa **Add file → Upload files** y arrastra **todo el contenido** de la
   carpeta (incluidas las carpetas `.github` y `gradle`, y los archivos `.gitignore` y `.gitattributes`).
   Pulsa **Commit changes**.
   - En Mac, Finder oculta las carpetas que empiezan por punto: pulsa `Cmd + Shift + .` para verlas.
   - Si la carpeta `.github` no se subiera, crea el workflow a mano: **Add file → Create new file**,
     escribe como nombre `.github/workflows/build.yml` y pega el contenido del archivo `build.yml`.
4. Ve a la pestaña **Actions**. La compilación empieza sola (o pulsa **Run workflow**). Tarda unos 5–10 minutos
   la primera vez porque descarga y prepara Minecraft.
5. Cuando aparezca el círculo verde ✔, entra en la ejecución y descarga el artefacto
   **CreatureStudio-NeoForge-1.21.1**. Dentro está el archivo `creaturestudio-neoforge-1.21.1-1.0.0.jar`.

Para compilar en tu PC: instala Java 21 y ejecuta `gradlew build` (Windows) o `./gradlew build` (Linux/Mac).
El `.jar` queda en `build/libs/`.

## Instalación

1. Instala **NeoForge 21.1.x** para **Minecraft 1.21.1** (https://neoforged.net).
2. Copia el `.jar` en la carpeta `mods` de tu `.minecraft`.
3. En un servidor, ponlo también en su carpeta `mods`.

## Cómo se usa

- **Abrir el estudio**: pulsa la tecla **K** (configurable en Controles) o haz clic derecho en el bloque
  **Estudio de Criaturas**. Receta (mesa de crafteo):

  ```
  Arcilla  Huevo   Arcilla
  Tablón   Libro   Tablón
  Tablón   Tablón  Tablón
  ```

- **Nueva** abre el editor con el selector de plantillas.
- En la lista de la izquierda eliges la pieza; a la derecha editas su **Forma**, **Aspecto** y **Animación**.
- Coordenadas en píxeles: 16 = 1 bloque. El suelo está en Y = 0 y el frente de la criatura mira hacia **-Z**
  (la flecha azul de la rejilla). «Centrar modelo» la centra y la apoya en el suelo.
- **Guardar** la sube al mundo. Después, en la biblioteca:
  - **Obtener huevo**: en creativo es gratis; en supervivencia cuesta 1 huevo de gallina.
  - **Invocar**: la hace aparecer delante de ti (solo creativo u operadores).
  - **Exportar / Importar**: archivos en `.minecraft/creaturestudio/`.
- Las criaturas de cada mundo se guardan en `<carpeta del mundo>/creaturestudio/*.json`.
- En multijugador, solo el creador de una criatura (o un operador) puede editarla o borrarla.
- Si borras una criatura, todas sus copias desaparecen del mundo.

## Estructura del proyecto

```
src/main/java/com/creaturestudio/
  CreatureStudio.java      Clase principal del mod
  data/                    Definición de criaturas, JSON, plantillas, jerarquía
  entity/                  La entidad que adopta cualquier forma y su IA
  item/ block/ registry/   Huevo, bloque y registros
  network/                 Paquetes cliente <-> servidor
  server/                  Guardado en el mundo, permisos y aparición natural
  client/                  Renderizado 3D, editor, biblioteca e importación
src/main/resources/        Texturas, modelos, idiomas (es/en), receta y botín
.github/workflows/build.yml  Compilación automática en GitHub
```

## Problemas frecuentes

- **El workflow falla en «Compilar el mod»**: abre el paso para ver el error. Si es un problema de red
  al descargar dependencias, vuelve a ejecutarlo con **Re-run jobs**.
- **No veo la criatura**: comprueba que el mod está instalado también en el servidor.
- **Una textura sale morada y negra**: el identificador no existe; elige una de la lista o revisa el nombre
  (por ejemplo `minecraft:block/stone`).

Licencia MIT.

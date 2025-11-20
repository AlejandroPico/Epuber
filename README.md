# EPUBER

Programa que gestiona EPUBS, MOBIS y PDF's

## Funcionalidades
- Exploración de biblioteca con filtros por extensión, fecha, tamaño mínimo y palabra clave.
- Copiador plano con control de sobrescritura y modo "añadir solo nuevos".
- Listado de libros sin necesidad de definir destino, con exportación de títulos y autores.
- Pestañas dedicadas para:
  - **Biblioteca**: acciones de copiado/listado y registro plegable estilo consola.
  - **Conversor PDF → EPUB**: conversión de maquetación fija, metadatos y portada opcional.
  - **Carátulas**: galería paginada con selección inmediata y editor de metadatos con búsqueda online.
  - **Duplicados**: detección de posibles ejemplares repetidos y eliminación directa.
- Lectura integrada de EPUB con ajustes de fuente, alineación, márgenes y modo oscuro.
- Visualización de carátulas de EPUB/PDF y acceso con doble clic al lector o aplicación del sistema.
- Generación de ficheros de metadatos auxiliares con información editada por el usuario.

## Notas
- El conversor utiliza PDFBox para rasterizar páginas y empaquetar el EPUB resultante.
- La búsqueda de metadatos se apoya en Open Library; requiere conexión a Internet.
- El registro puede copiarse al portapapeles desde su cabecera desplegable.

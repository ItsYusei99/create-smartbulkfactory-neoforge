# Create: Smart Bulk Factory

Addon for Create 6.0.x (NeoForge 1.21.1).

- **Bulk crafting**: a Factory Gauge in crafting mode requests up to N crafts at once (config `maxBatchCrafts`,
  per-gauge button). The package carries N crafts; Mechanical Crafters receive N items per slot and craft the whole
  batch in one pass, like a crafting table. Remainders (buckets...) scale with the batch.
- **Variants**: with the per-gauge toggle ON, an ingredient may be replaced by any item that shares a tag with it,
  is accepted by the same recipe, and is in stock on the network (any planks, any wool...).

Limits: a batch must fit one package (9 slots), ingredient stack size, and the result must fit one stack.
Build: `./gradlew build` (needs `libs/create-1.21.1-6.0.10.jar`, not included).

# Recruits 1.15.2 for Minecraft 1.21.1 / NeoForge 21.1.233

This branch adapts Recruits 1.15.2 using nekomario28's NeoForge migration
(`ecfe4b59167bb72460ce7b015ff6391eb39d0463`) and retains the dedicated-server
hiring and unloaded-client-config sound fixes from this fork.

## Install
- Use Minecraft **1.21.1**, NeoForge **21.1.233**, and Java **21**.
- Install the same `recruits-1.21.1-1.15.2-neoforge.233.1-all.jar` on the server and every client.
- Remove older Recruits JARs. CoreLib is bundled and relocated; no separate CoreLib download is needed.
- Back up existing worlds before testing a mod migration. Importing a Forge 1.20.1 world is not covered by the checks below.
- The original author's All Rights Reserved license remains in effect.

## Verification
The workflow builds the shaded production JAR and runs:
- a regression test for shared sound code when client configuration is unloaded;
- server GameTests for paid hiring, owner assignment, follow state, currency deduction,
  insufficient currency rejection, entity save/load (UUID, owner, custom name, equipment,
  inventory), and creation/equipment of all six basic unit types;
- a fresh NeoForge 21.1.233 dedicated server with only the packaged mod, recruit spawning,
  world saving, restart, and restoration of the saved recruit;
- a virtual client startup check for renderer/resource loading.

These checks do not cover every GUI interaction, full multiplayer packet round trips,
combat AI, or optional third-party mod compatibility.

# TV 30B — Home Corrective Polish

Real-TV validation of 30A showed two issues: the collapsed sidebar affordance looked orphaned at the top-left, and Home still felt visually under-filled even though the hero height itself was acceptable.

30B is corrective, not a new architecture:

- Hero remains capped around the upper half of the viewport.
- Hero copy gets a wider reading column and slightly stronger TV typography; backdrop scrims are rebalanced rather than enlarged.
- Rails begin earlier (~46.5% viewport) to remove dead space between hero copy and Continue Watching.
- Continue Watching is a medium 238dp 16:9 card; normal catalog posters are medium 148dp 2:3 cards.
- Row labels and card metadata are slightly larger for sofa distance while focus scale remains restrained.
- The 29C.4 expanded sidebar panel is no longer left invisibly composed at rest; it exists only while open/animating closed and restores current-route focus one frame after opening.
- The collapsed route affordance moves from the top-left hero corner to center-left and uses a cleaner compact pill/icon treatment.
- Search may still hide the collapsed route affordance as before.
- Routing, Home data, playback, Shared Core, Mobile and exact return-to-content semantics are unchanged.

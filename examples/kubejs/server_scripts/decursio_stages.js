// These methods operate on Decursio Stages, independently of KubeJS player.stages.
PlayerEvents.loggedIn(event => {
  DecursioStages.add(event.player, 'age_1') // idempotent; returns whether added
  console.info('Decursio stages: ' + DecursioStages.getStages(event.player))
})

PlayerEvents.inventoryChanged(event => {
  if (event.item.id === 'minecraft:diamond' && !DecursioStages.has(event.player, 'age_2')) {
    DecursioStages.add(event.player, 'age_2')
  }
})

// DecursioStages.remove(player, 'age_2') returns whether removed.
// DecursioStages.clear(player) returns the number of stages cleared.

DecursioStagesEvents.stageAdding(event => {
  // Before the change: event.cancel() prevents granting this stage.
  if (event.stage === 'age_3' && !DecursioStages.has(event.player, 'age_2')) {
    event.cancel()
  }
})

DecursioStagesEvents.stageAdded(event => {
  event.player.tell('Stage unlocked: ' + event.stage)
})

DecursioStagesEvents.stageRemoving(event => {
  // Optional: prevent removal of the starter stage through individual removals.
  // Bulk clear is a separate operation and does not fire stageRemoving.
  if (event.stage === 'age_1') event.cancel()
})

DecursioStagesEvents.stageRemoved(event => {
  event.player.tell('Stage removed: ' + event.stage)
})

DecursioStagesEvents.stagesCleared(event => {
  event.player.tell('All Decursio stages cleared.')
})

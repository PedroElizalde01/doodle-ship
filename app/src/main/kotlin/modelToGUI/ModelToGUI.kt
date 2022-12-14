package modelToGUI

import edu.austral.ingsis.starships.ui.ElementColliderType
import edu.austral.ingsis.starships.ui.ElementModel
import edu.austral.ingsis.starships.ui.KeyReleased
import factory.createAsteroid
import factory.createPowerUp
import javafx.scene.input.KeyCode
import javafx.scene.control.Label
import model.Action
import model.Game
import model.ShipMovement
import model.States
import model.asteroid.Asteroid
import model.bullet.Bullet
import model.Movable
import model.powerUp.PowerUp
import model.starship.Starship

class ModelToGUI(val game: Game,private val spawnProbs:Int) {
    private fun adaptStarship(starship: Starship): ElementModel {
        return ElementModel(
            starship.id(),
            starship.x(),
            starship.y(),
            starship.skin.height,
            starship.skin.width,
            starship.vector.rotation,
            ElementColliderType.Elliptical,
            starship.skin
        )
    }

    private fun adaptAsteroid(asteroid: Asteroid):ElementModel{
        return ElementModel(
            asteroid.id(),
            asteroid.x(),
            asteroid.y(),
            asteroid.size,
            asteroid.size,
            asteroid.vector.rotation,
            ElementColliderType.Elliptical,
            asteroid.skin
        )
    }

    private fun adaptBullet(bullet: Bullet):ElementModel{
        return ElementModel(
            bullet.id(),
            bullet.pos.x,
            bullet.pos.y,
            bullet.skin.height,
            bullet.skin.width,
            bullet.vector.rotation,
            ElementColliderType.Rectangular,
            bullet.skin
        )
    }

    private fun adaptPowerUp(power: PowerUp):ElementModel{
        return ElementModel(
            power.id(),
            power.pos.x,
            power.pos.y,
            power.skin.height,
            power.skin.width,
            power.vector.rotation,
            ElementColliderType.Elliptical,
            power.skin
        )
    }

    private fun accelerate(id:String):ModelToGUI{
        val starship = (game.movables.find{it.id() == id} as Starship)
        val newShips = game.movables.filter { it.id() != id }
        return ModelToGUI(game.copy(movables = newShips.plus(starship.accelerate())),spawnProbs)
    }

    private fun stop(id:String) : ModelToGUI {
        val starship = (game.movables.find { it.id() == id} as Starship)
        val newShips = game.movables.filter {it.id() != id}
        return ModelToGUI(game.copy(movables = newShips.plus(starship.stop())),spawnProbs)
    }

    private fun rotateLeft(id: String, time: Double) : ModelToGUI{
        if(game.state == States.RUNNING){
            val starship = (game.movables.find { it.id() == id} as Starship)
            val newShips = game.movables.filter {it.id() != id}
            return ModelToGUI(game.copy(movables = newShips.plus(starship.rotateLeft(time))),spawnProbs)
        }
        return this
    }

    private fun rotateRight(id: String, time: Double) : ModelToGUI{
        if(game.state == States.RUNNING){
            val starship = (game.movables.find { it.id() == id} as Starship)
            val newShips = game.movables.filter {it.id() != id}
            return ModelToGUI(game.copy(movables = newShips.plus(starship.rotateRight(time))),spawnProbs)
        }
        return this
    }

    private fun shoot(id: String): ModelToGUI{
        if(game.state == States.RUNNING){
            val starship = (game.movables.find {it.id() == id} as Starship)
            val currentBullets = game.movables.filter { mov -> mov is Bullet &&  mov.shipId == starship.id}
            if(currentBullets.size >= 6) return this
            return ModelToGUI(game.copy(movables = game.movables + starship.shoot()),spawnProbs)
        }
        return this
    }

    fun collision(id1: String, id2:String, elements:MutableMap<String, ElementModel>):ModelToGUI{
        var remainingMovables = game.movables.filter { it.id() != id1 && it.id() != id2 }
        val movable1 = game.movables.find { it.id() == id1 }
        val movable2 = game.movables.find { it.id() == id2 }
        if ( movable1 == null || movable2 == null) return this
        val collide1 = movable1.collision(movable2)
        val collide2 = movable2.collision(movable1)
        if(collide1.life() > 0) remainingMovables = remainingMovables.plus(collide1)
        if(collide2.life() > 0) remainingMovables = remainingMovables.plus(collide2)
        val newModelToGUI = ModelToGUI(game.copy(movables = remainingMovables),spawnProbs)
        val removedMovables = game.movables.filter { movable -> !remainingMovables.any {newMovable -> newMovable.id() == movable.id() } }
        removedMovables.forEach { elements.remove(it.id())}
        return newModelToGUI
    }

    fun pressedKey(key: KeyCode, time: Double) : ModelToGUI {
        if(game.movementKeyMap.containsKey(key)){
            val action = game.movementKeyMap.getValue(key)
            return when(action.movement){
                ShipMovement.ACCELERATE -> accelerate(action.id)
                ShipMovement.STOP -> stop(action.id)
                ShipMovement.ROTATE_LEFT -> rotateLeft(action.id, time)
                ShipMovement.ROTATE_RIGHT -> rotateRight(action.id, time)
                else -> this
            }
        }
        return this
    }

    fun releasedKey(key: KeyReleased):ModelToGUI{
        if(game.releaseKeyMap.containsKey(key.key)){
            val action = game.releaseKeyMap.getValue(key.key)
            return when(action.action){
                Action.TOGGLE_PAUSE -> togglePause()
                Action.SHOOT -> shoot(action.id)
                else -> this
            }
        }
        return this
    }

    fun togglePause() : ModelToGUI {
        if(game.state == States.RUNNING) return ModelToGUI(game.copy(state = States.PAUSED),spawnProbs)
        return ModelToGUI(game.copy(state = States.RUNNING),spawnProbs)
    }

    fun keyFramePassed(time : Double) : ModelToGUI{
        if(game.state === States.RUNNING){
            val n = (0..spawnProbs).random()
            if(n <= 150){ // 0,15 % chance to spawn an powerUp
                return spawnPowerUp(time)
            }
            if(n <= 450) { // 0,45 % chance to spawn an asteroid
                return spawnAsteroid(time)
            }
            return ModelToGUI(game.copy(movables = game.movables.map { it.move(time) }), spawnProbs)
        }
        return this
    }

    private fun spawnAsteroid(time: Double): ModelToGUI {
        val newMovables = createAsteroid(game.movables, "a" + (0..1000).random())
        return ModelToGUI(game.copy(movables = newMovables.map { it.move(time) }), spawnProbs)
    }

    private fun spawnPowerUp(time: Double): ModelToGUI {
        val newMovables = createPowerUp(game.movables, "p" + (0..1000).random())
        return ModelToGUI(game.copy(movables = newMovables.map { it.move(time) }), spawnProbs)
    }

    fun adaptElements(elements : Map<String, ElementModel>) : ModelToGUI {
        game.movables.forEach {
            elements.getValue(it.id()).x.set(it.x())
            elements.getValue(it.id()).y.set(it.y())
            elements.getValue(it.id()).rotationInDegrees.set(it.rotation())
        }
        return this
    }

    fun addElements(elements:MutableMap<String, ElementModel>):ModelToGUI{
        val newElements = game.movables
        newElements.forEach{elements[it.id()] = elementToUI(it)}
        return this
    }

    private fun elementToUI(movable: Movable) : ElementModel {
        return when (movable) {
            is Starship -> adaptStarship(movable)
            is Asteroid -> adaptAsteroid(movable)
            is Bullet   -> adaptBullet(movable)
            is PowerUp  -> adaptPowerUp(movable)
            else -> adaptAsteroid(movable as Asteroid)
        }
    }

    fun updateLives(id : String) : Label {
        return when(val element = game.movables.find {it.id() === id}){
            is Starship -> Label("Life: " + element.life())
            else -> { Label("") }
        }
    }
}
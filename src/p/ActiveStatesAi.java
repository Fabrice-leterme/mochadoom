/*
 * Copyright (C) 1993-1996 by id Software, Inc.
 * Copyright (C) 2017 Good Sign
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package p;

import static data.Tables.ANG45;
import static data.Tables.BITS32;
import data.mobjtype_t;
import data.sounds;
import defines.skill_t;
import defines.statenum_t;
import p.ActionSystem.AbstractCommand;

import static p.mobj_t.MF_AMBUSH;
import static p.mobj_t.MF_COUNTKILL;
import static p.mobj_t.MF_JUSTATTACKED;
import static p.mobj_t.MF_SHADOW;
import static p.mobj_t.MF_SHOOTABLE;
import static p.mobj_t.MF_SKULLFLY;
import static p.mobj_t.MF_SOLID;
import static utils.C2JUtils.eval;

interface ActiveStatesAi<R extends Actions.Registry & AbstractCommand<R>> extends ActionsMovement<R>, ActionsThinkers<R> {
    //
    // A_Look
    // Stay in state until a player is sighted.
    //
    default void A_Look(mobj_t actor) {
        final p.Actions.Registry obs = obs();
        mobj_t targ;
        boolean seeyou = false; // to avoid the fugly goto

        actor.threshold = 0;   // any shot will wake up
        targ = actor.subsector.sector.soundtarget;

        if (targ != null
            && eval(targ.flags & MF_SHOOTABLE)) {
            actor.target = targ;

            if (eval(actor.flags & MF_AMBUSH)) {
                seeyou = (obs.EN.CheckSight(actor, actor.target));
            } else {
                seeyou = true;
            }
        }
        if (!seeyou) {
            if (!obs.EN.LookForPlayers(actor, false)) {
                return;
            }
        }

        // go into chase state
        seeyou:
        if (actor.info.seesound != null && actor.info.seesound != sounds.sfxenum_t.sfx_None) {
            int sound;

            switch (actor.info.seesound) {
                case sfx_posit1:
                case sfx_posit2:
                case sfx_posit3:
                    sound = sounds.sfxenum_t.sfx_posit1.ordinal() + obs.DOOM.random.P_Random() % 3;
                    break;

                case sfx_bgsit1:
                case sfx_bgsit2:
                    sound = sounds.sfxenum_t.sfx_bgsit1.ordinal() + obs.DOOM.random.P_Random() % 2;
                    break;

                default:
                    sound = actor.info.seesound.ordinal();
                    break;
            }

            if (actor.type == mobjtype_t.MT_SPIDER
                || actor.type == mobjtype_t.MT_CYBORG) {
                // full volume
                obs.DOOM.doomSound.StartSound(null, sound);
            } else {
                obs.DOOM.doomSound.StartSound(actor, sound);
            }
        }

        actor.SetMobjState(actor.info.seestate);
    }

    /**
     * A_Chase
     * Actor has a melee attack,
     * so it tries to close as fast as possible
     */
    default void A_Chase(mobj_t actor) {
        final p.Actions.Registry obs = obs();
        int delta;
        boolean nomissile = false; // for the fugly goto

        if (actor.reactiontime != 0) {
            actor.reactiontime--;
        }

        // modify target threshold
        if (actor.threshold != 0) {
            if (actor.target == null || actor.target.health <= 0) {
                actor.threshold = 0;
            } else {
                actor.threshold--;
            }
        }

        // turn towards movement direction if not there yet
        if (actor.movedir < 8) {
            actor.angle &= (7 << 29);
            actor.angle &= BITS32;
            // Nice problem, here!
            delta = (int) (actor.angle - (actor.movedir << 29));

            if (delta > 0) {
                actor.angle -= ANG45;
            } else if (delta < 0) {
                actor.angle += ANG45;
            }

            actor.angle &= BITS32;
        }

        if (actor.target == null || !eval(actor.target.flags & MF_SHOOTABLE)) {
            // look for a new target
            if (obs.EN.LookForPlayers(actor, true)) {
                return;     // got a new target
            }
            actor.SetMobjState(actor.info.spawnstate);
            return;
        }

        // do not attack twice in a row
        if (eval(actor.flags & MF_JUSTATTACKED)) {
            actor.flags &= ~MF_JUSTATTACKED;
            if (obs.DOOM.gameskill != skill_t.sk_nightmare && !obs.DOOM.fastparm) {
                NewChaseDir(actor);
            }
            return;
        }

        // check for melee attack
        if (actor.info.meleestate != statenum_t.S_NULL && obs.EN.CheckMeleeRange(actor)) {
            if (actor.info.attacksound != null) {
                obs.DOOM.doomSound.StartSound(actor, actor.info.attacksound);
            }
            actor.SetMobjState(actor.info.meleestate);
            return;
        }

        // check for missile attack
        if (actor.info.missilestate != statenum_t.S_NULL) { //_D_: this caused a bug where Demon for example were disappearing
            // Assume that a missile attack is possible
            if (obs.DOOM.gameskill.ordinal() < skill_t.sk_nightmare.ordinal()
                && !obs.DOOM.fastparm && actor.movecount != 0) {
                // Uhm....no.
                nomissile = true;
            } else if (!obs.EN.CheckMissileRange(actor)) {
                nomissile = true; // Out of range
            }
            if (!nomissile) {
                // Perform the attack
                actor.SetMobjState(actor.info.missilestate);
                actor.flags |= MF_JUSTATTACKED;
                return;
            }
        }

        // This should be executed always, if not averted by returns.
        // possibly choose another target
        if (obs.DOOM.netgame && actor.threshold == 0 && !obs.EN.CheckSight(actor, actor.target)) {
            if (obs.EN.LookForPlayers(actor, true)) {
                return; // got a new target
            }
        }

        // chase towards player
        if (--actor.movecount < 0 || !Move(actor)) {
            NewChaseDir(actor);
        }

        // make active sound
        if (actor.info.activesound != null && obs.DOOM.random.P_Random() < 3) {
            obs.DOOM.doomSound.StartSound(actor, actor.info.activesound);
        }
    }

    default void A_Fall(mobj_t actor) {
        // actor is on ground, it can be walked over
        actor.flags &= ~MF_SOLID;

        // So change this if corpse objects
        // are meant to be obstacles.
    }

    /**
     * Causes object to move and perform obs.
     * Can only be called through the Actions dispatcher.
     *
     * @param mobj
     */
    //
    //P_MobjThinker
    //
    default void P_MobjThinker(mobj_t mobj) {
        final p.Actions.Registry obs = obs();
        // momentum movement
        if (mobj.momx != 0 || mobj.momy != 0 || (eval(mobj.flags & MF_SKULLFLY))) {
            XYMovement(mobj);

            // FIXME: decent NOP/NULL/Nil function pointer please.
            if (mobj.thinkerFunction == ActiveStates.NOP) {
                return; // mobj was removed
            }
        }
        if ((mobj.z != mobj.floorz) || mobj.momz != 0) {
            mobj.ZMovement();

            // FIXME: decent NOP/NULL/Nil function pointer please.
            if (mobj.thinkerFunction == ActiveStates.NOP) {
                return; // mobj was removed
            }
        }

        // cycle through states,
        // calling action functions at transitions
        if (mobj.mobj_tics != -1) {
            mobj.mobj_tics--;

            // you can cycle through multiple states in a tic
            if (!eval(mobj.mobj_tics)) {
                if (!mobj.SetMobjState(mobj.mobj_state.nextstate)) {
                    // freed itself
                }
            }
        } else {
            // check for nightmare respawn
            if (!eval(mobj.flags & MF_COUNTKILL)) {
                return;
            }

            if (!obs.DOOM.respawnmonsters) {
                return;
            }

            mobj.movecount++;

            if (mobj.movecount < 12 * 35) {
                return;
            }

            if (eval(obs.DOOM.leveltime & 31)) {
                return;
            }

            if (obs.DOOM.random.P_Random() > 4) {
                return;
            }

            NightmareRespawn(mobj);
        }
    }
    
    //
    // A_FaceTarget
    //
    default void A_FaceTarget(mobj_t actor) {
        final p.Actions.Registry obs = obs();
        if (actor.target == null) {
            return;
        }

        actor.flags &= ~MF_AMBUSH;

        actor.angle = obs.DOOM.sceneRenderer.PointToAngle2(actor.x,
            actor.y,
            actor.target.x,
            actor.target.y) & BITS32;

        if (eval(actor.target.flags & MF_SHADOW)) {
            actor.angle += (obs.DOOM.random.P_Random() - obs.DOOM.random.P_Random()) << 21;
        }
        actor.angle &= BITS32;
    }
}
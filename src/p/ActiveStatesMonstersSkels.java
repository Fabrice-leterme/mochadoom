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

import data.Tables;
import static data.Tables.ANG180;
import static data.Tables.BITS32;
import static data.Tables.finecosine;
import static data.Tables.finesine;
import data.mobjtype_t;
import data.sounds;
import p.ActionSystem.AbstractCommand;

import static m.fixed_t.FRACUNIT;
import static m.fixed_t.FixedMul;
import static m.fixed_t.MAPFRACUNIT;
import static p.Actions.Registry.TRACEANGLE;
import static p.MapUtils.AproxDistance;
import static utils.C2JUtils.eval;

interface ActiveStatesMonstersSkels<R extends Actions.Registry & AbstractCommand<R>> extends ActiveStatesAi<R>, ActionsMissiles<R> {

    //
    // A_SkelMissile
    //
    default void A_SkelMissile(mobj_t actor) {
        mobj_t mo;

        if (actor.target == null) {
            return;
        }

        A_FaceTarget(actor);
        actor.z += 16 * FRACUNIT;    // so missile spawns higher
        mo = SpawnMissile(actor, actor.target, mobjtype_t.MT_TRACER);
        actor.z -= 16 * FRACUNIT;    // back to normal

        mo.x += mo.momx;
        mo.y += mo.momy;
        mo.tracer = actor.target;
    }

    default void A_SkelWhoosh(mobj_t actor) {
        final Actions.Registry obs = obs();
        if (actor.target == null) {
            return;
        }
        A_FaceTarget(actor);
        obs.DOOM.doomSound.StartSound(actor, sounds.sfxenum_t.sfx_skeswg);
    }

    default void A_SkelFist(mobj_t actor) {
        final Actions.Registry obs = obs();
        int damage;

        if (actor.target == null) {
            return;
        }

        A_FaceTarget(actor);

        if (obs.EN.CheckMeleeRange(actor)) {
            damage = ((obs.DOOM.random.P_Random() % 10) + 1) * 6;
            obs.DOOM.doomSound.StartSound(actor, sounds.sfxenum_t.sfx_skepch);
            DamageMobj(actor.target, actor, actor, damage);
        }
    }
    
    default void A_Tracer(mobj_t actor) {
        final Actions.Registry obs = obs();
        long exact; //angle_t
        int dist, slope; // fixed
        mobj_t dest;
        mobj_t th;
        if (eval(obs.DOOM.gametic & 3)) {
            return;
        }
        // spawn a puff of smoke behind the rocket
        SpawnPuff(actor.x, actor.y, actor.z);
        th = SpawnMobj(actor.x - actor.momx,
            actor.y - actor.momy,
            actor.z, mobjtype_t.MT_SMOKE);
        th.momz = MAPFRACUNIT;
        th.mobj_tics -= obs.DOOM.random.P_Random() & 3;
        if (th.mobj_tics < 1) {
            th.mobj_tics = 1;
        }
        // adjust direction
        dest = actor.tracer;
        if (dest == null || dest.health <= 0) {
            return;
        }
        // change angle
        exact = obs.DOOM.sceneRenderer.PointToAngle2(actor.x,
            actor.y,
            dest.x,
            dest.y) & BITS32;
        // MAES: let's analyze the logic here...
        // So exact is the angle between the missile and its target.
        if (exact != actor.angle) // missile is already headed there dead-on.
        {
            if (exact - actor.angle > ANG180) {
                actor.angle -= TRACEANGLE;
                actor.angle &= BITS32;
                if (((exact - actor.angle) & BITS32) < ANG180) {
                    actor.angle = exact;
                }
            } else {
                actor.angle += TRACEANGLE;
                actor.angle &= BITS32;
                if (((exact - actor.angle) & BITS32) > ANG180) {
                    actor.angle = exact;
                }
            }
        }
        // MAES: fixed and sped up.
        int exact2 = Tables.toBAMIndex(actor.angle);
        actor.momx = FixedMul(actor.info.speed, finecosine[exact2]);
        actor.momy = FixedMul(actor.info.speed, finesine[exact2]);
        // change slope
        dist = AproxDistance(dest.x - actor.x,
            dest.y - actor.y);
        dist /= actor.info.speed;
        if (dist < 1) {
            dist = 1;
        }
        slope = (dest.z + 40 * FRACUNIT - actor.z) / dist;
        if (slope < actor.momz) {
            actor.momz -= FRACUNIT / 8;
        } else {
            actor.momz += FRACUNIT / 8;
        }
    }

}